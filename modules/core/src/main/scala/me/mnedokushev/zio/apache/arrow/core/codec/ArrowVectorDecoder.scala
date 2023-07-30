package me.mnedokushev.zio.apache.arrow.core.codec

import org.apache.arrow.vector._
import org.apache.arrow.vector.complex.{ ListVector, StructVector }
import org.apache.arrow.vector.complex.impl._
import org.apache.arrow.vector.complex.reader.FieldReader
import zio._
import zio.schema._

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

trait ArrowVectorDecoder[Vector <: ValueVector, +Val] extends ArrowDecoder[Vector, Val] { self =>

  override final def decode(from: Vector): Either[Throwable, Chunk[Val]] =
    try
      Right(decodeUnsafe(from))
    catch {
      case NonFatal(ex) => Left(ArrowDecoderError("Error decoding vector", Some(ex)))
    }

  protected def decodeUnsafe(from: Vector): Chunk[Val]

//  def flatMap[B](f: Val => ArrowVectorDecoder[Vector, B]): ArrowVectorDecoder[Vector, B] =
//    new ArrowVectorDecoder[Vector, B] {
//      override protected def decodeUnsafe(from: Vector): Chunk[B] =
//        self.decodeUnsafe(from).flatMap(v => f(v).decodeUnsafe(from))
//    }
//
//  def map[B](f: Val => B): ArrowVectorDecoder[Vector, B] =
//    new ArrowVectorDecoder[Vector, B] {
//      override protected def decodeUnsafe(from: Vector): Chunk[B] =
//        self.decodeUnsafe(from).map(f)
//    }

}

object ArrowVectorDecoder {

  def apply[Vector <: ValueVector, Val](implicit
    decoder: ArrowVectorDecoder[Vector, Val]
  ): ArrowVectorDecoder[Vector, Val] =
    decoder

  implicit def primitive[Vector <: ValueVector, Val](implicit schema: Schema[Val]): ArrowVectorDecoder[Vector, Val] =
    new ArrowVectorDecoder[Vector, Val] {
      override protected def decodeUnsafe(from: Vector): Chunk[Val] =
        schema match {
          case Schema.Primitive(standardType, _) =>
            var idx        = 0
            val valueCount = from.getValueCount
            val builder    = ChunkBuilder.make[Val](valueCount)
            val reader     = from.getReader

            while (idx < valueCount) {
              reader.setPosition(idx)
              val dynamicValue = decodePrimitive(standardType, reader)

              dynamicValue.toTypedValue match {
                case Right(v)      =>
                  builder.addOne(v)
                  idx += 1
                case Left(message) =>
                  throw ArrowDecoderError(message)
              }
            }

            builder.result()
          case _                                 =>
            throw ArrowDecoderError(s"Given ZIO schema must be of type Schema.Primitive[Val]")
        }
    }

  implicit def list[Val, Col[x] <: Iterable[x]](implicit
    schema: Schema[Val]
  ): ArrowVectorDecoder[ListVector, Col[Val]] =
    new ArrowVectorDecoder[ListVector, Col[Val]] {
      override protected def decodeUnsafe(from: ListVector): Chunk[Col[Val]] = {
        var idx        = 0
        val valueCount = from.getValueCount
        val builder    = ChunkBuilder.make[Col[Val]](valueCount)
        val reader     = from.getReader

        while (idx < valueCount) {
          val buffer = ListBuffer.empty[Val]

          reader.setPosition(idx)
          while (reader.next())
            if (reader.isSet) {
              val dynamicValue = decodeSchema(None, schema, reader)

              dynamicValue.toTypedValue match {
                case Right(v)      => buffer.addOne(v)
                case Left(message) => throw ArrowDecoderError(message)
              }
            }

          builder.addOne(buffer.result().asInstanceOf[Col[Val]])
          idx += 1
        }

        builder.result()
      }
    }

  implicit def struct[Val](implicit schema: Schema[Val]): ArrowVectorDecoder[StructVector, Val] =
    new ArrowVectorDecoder[StructVector, Val] {
      override protected def decodeUnsafe(from: StructVector): Chunk[Val] =
        schema match {
          case record: Schema.Record[Val] =>
            var idx        = 0
            val valueCount = from.getValueCount
            val builder    = ChunkBuilder.make[Val](valueCount)
            val reader     = from.getReader

            while (idx < valueCount) {
              reader.setPosition(idx)
              val dynamicValue = decodeCaseClass(record.fields, reader)

              dynamicValue.toTypedValue match {
                case Right(v)      =>
                  builder.addOne(v)
                  idx += 1
                case Left(message) =>
                  throw ArrowDecoderError(message)
              }
            }

            builder.result()
          case _                          =>
            throw ArrowDecoderError(s"Given ZIO schema must be of type Schema.Record[Val]")
        }
    }

  @tailrec
  private def decodeSchema[A](name: Option[String], schema0: Schema[A], reader0: FieldReader): DynamicValue =
    schema0 match {
      case record: Schema.Record[A]                =>
        val reader = name.fold[FieldReader](reader0.reader())(reader0.reader(_))
        decodeCaseClass(record.fields, reader)
      case Schema.Primitive(standardType, _)       =>
        val reader = name.fold[FieldReader](reader0.reader())(reader0.reader(_))
        decodePrimitive(standardType, reader)
      case Schema.Sequence(elemSchema, _, _, _, _) =>
        val reader = name.fold[FieldReader](reader0.reader())(reader0.reader(_))
        decodeSequence(elemSchema, reader)
      case lzy: Schema.Lazy[_]                     =>
        decodeSchema(name, lzy.schema, reader0)
      case other                                   =>
        throw ArrowDecoderError(s"Unsupported ZIO Schema type $other")
    }

  private def decodeCaseClass[A](fields: Chunk[Schema.Field[A, _]], reader0: FieldReader): DynamicValue = {
    val values = fields.map { case Schema.Field(name, schema0, _, _, _, _) =>
      val value: DynamicValue = decodeSchema(Some(name), schema0, reader0)

      name -> value
    }.to(ListMap)

    DynamicValue.Record(TypeId.Structural, values)
  }

  private def decodeSequence[A](schema0: Schema[A], reader0: FieldReader): DynamicValue = {
    val builder = ChunkBuilder.make[DynamicValue]()

    while (reader0.next())
      if (reader0.isSet)
        builder.addOne(decodeSchema(None, schema0, reader0))

    DynamicValue.Sequence(builder.result())
  }

  private def decodePrimitive[A](standardType: StandardType[A], reader0: FieldReader): DynamicValue =
    standardType match {
      case t: StandardType.BoolType.type   =>
        DynamicValue.Primitive[Boolean](reader0.readBoolean(), t)
      case t: StandardType.IntType.type    =>
        DynamicValue.Primitive[Int](reader0.readInteger(), t)
      case t: StandardType.LongType.type   =>
        DynamicValue.Primitive[Long](reader0.readLong(), t)
      case t: StandardType.FloatType.type  =>
        DynamicValue.Primitive[Float](reader0.readFloat(), t)
      case t: StandardType.DoubleType.type =>
        DynamicValue.Primitive[Double](reader0.readDouble(), t)
      case t: StandardType.StringType.type =>
        DynamicValue.Primitive[String](reader0.readText().toString, t)
      case other                           =>
        throw ArrowDecoderError(s"Unsupported ZIO Schema type $other")
    }

}
