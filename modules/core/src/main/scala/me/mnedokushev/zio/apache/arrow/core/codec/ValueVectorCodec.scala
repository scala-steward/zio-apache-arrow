package me.mnedokushev.zio.apache.arrow.core.codec

import org.apache.arrow.memory.BufferAllocator
import org.apache.arrow.vector.complex.{ ListVector, StructVector }
import org.apache.arrow.vector.{ ValueVector, _ }
import zio._
import zio.schema.Schema

final case class ValueVectorCodec[V <: ValueVector, A](
  encoder: ValueVectorEncoder[V, A],
  decoder: ValueVectorDecoder[V, A]
) { self =>

  def decodeZIO(vec: V): Task[Chunk[A]] =
    decoder.decodeZIO(vec)

  def decode(vec: V): Either[Throwable, Chunk[A]] =
    decoder.decode(vec)

  def encodeZIO(chunk: Chunk[A]): RIO[Scope & BufferAllocator, V] =
    encoder.encodeZIO(chunk)

  def encode(chunk: Chunk[A])(implicit alloc: BufferAllocator): Either[Throwable, V] =
    encoder.encode(chunk)

  def transform[B](f: A => B, g: B => A)(implicit schemaSrc: Schema[A], schemaDst: Schema[B]): ValueVectorCodec[V, B] =
    ValueVectorCodec(encoder.contramap(g), decoder.map(f))

}

object ValueVectorCodec {

  implicit def codec[V <: ValueVector, A](implicit
    encoder: ValueVectorEncoder[V, A],
    decoder: ValueVectorDecoder[V, A]
  ): ValueVectorCodec[V, A] =
    ValueVectorCodec[V, A](encoder, decoder)

  implicit val stringCodec: ValueVectorCodec[VarCharVector, String]                           =
    codec[VarCharVector, String]
  implicit val boolCodec: ValueVectorCodec[BitVector, Boolean]                                =
    codec[BitVector, Boolean]
  implicit val byteCodec: ValueVectorCodec[UInt1Vector, Byte]                                 =
    codec[UInt1Vector, Byte]
  implicit val shortCodec: ValueVectorCodec[SmallIntVector, Short]                            =
    codec[SmallIntVector, Short]
  implicit val intCodec: ValueVectorCodec[IntVector, Int]                                     =
    codec[IntVector, Int]
  implicit val longCodec: ValueVectorCodec[BigIntVector, Long]                                =
    codec[BigIntVector, Long]
  implicit val floatCodec: ValueVectorCodec[Float4Vector, Float]                              =
    codec[Float4Vector, Float]
  implicit val doubleCodec: ValueVectorCodec[Float8Vector, Double]                            =
    codec[Float8Vector, Double]
  implicit val binaryCodec: ValueVectorCodec[LargeVarBinaryVector, Chunk[Byte]]               =
    codec[LargeVarBinaryVector, Chunk[Byte]]
  implicit val charCodec: ValueVectorCodec[UInt2Vector, Char]                                 =
    codec[UInt2Vector, Char]
  implicit val uuidCodec: ValueVectorCodec[VarBinaryVector, java.util.UUID]                   =
    codec[VarBinaryVector, java.util.UUID]
  implicit val bigDecimalCodec: ValueVectorCodec[DecimalVector, java.math.BigDecimal]         =
    codec[DecimalVector, java.math.BigDecimal]
  implicit val bigIntegerCodec: ValueVectorCodec[VarBinaryVector, java.math.BigInteger]       =
    codec[VarBinaryVector, java.math.BigInteger]
  implicit val dayOfWeekCodec: ValueVectorCodec[IntVector, java.time.DayOfWeek]               =
    codec[IntVector, java.time.DayOfWeek]
  implicit val monthCodec: ValueVectorCodec[IntVector, java.time.Month]                       =
    codec[IntVector, java.time.Month]
  implicit val monthDayCodec: ValueVectorCodec[BigIntVector, java.time.MonthDay]              =
    codec[BigIntVector, java.time.MonthDay]
  implicit val periodCodec: ValueVectorCodec[VarBinaryVector, java.time.Period]               =
    codec[VarBinaryVector, java.time.Period]
  implicit val yearCodec: ValueVectorCodec[IntVector, java.time.Year]                         =
    codec[IntVector, java.time.Year]
  implicit val yearMonthCodec: ValueVectorCodec[BigIntVector, java.time.YearMonth]            =
    codec[BigIntVector, java.time.YearMonth]
  implicit val zoneIdCodec: ValueVectorCodec[VarCharVector, java.time.ZoneId]                 =
    codec[VarCharVector, java.time.ZoneId]
  implicit val zoneOffsetCodec: ValueVectorCodec[VarCharVector, java.time.ZoneOffset]         =
    codec[VarCharVector, java.time.ZoneOffset]
  implicit val durationCodec: ValueVectorCodec[BigIntVector, Duration]                        =
    codec[BigIntVector, Duration]
  implicit val instantCodec: ValueVectorCodec[BigIntVector, java.time.Instant]                =
    codec[BigIntVector, java.time.Instant]
  implicit val localDateCodec: ValueVectorCodec[VarCharVector, java.time.LocalDate]           =
    codec[VarCharVector, java.time.LocalDate]
  implicit val localTimeCodec: ValueVectorCodec[VarCharVector, java.time.LocalTime]           =
    codec[VarCharVector, java.time.LocalTime]
  implicit val localDateTimeCodec: ValueVectorCodec[VarCharVector, java.time.LocalDateTime]   =
    codec[VarCharVector, java.time.LocalDateTime]
  implicit val offsetTimeCodec: ValueVectorCodec[VarCharVector, java.time.OffsetTime]         =
    codec[VarCharVector, java.time.OffsetTime]
  implicit val offsetDateTimeCodec: ValueVectorCodec[VarCharVector, java.time.OffsetDateTime] =
    codec[VarCharVector, java.time.OffsetDateTime]
  implicit val zonedDateTimeCodec: ValueVectorCodec[VarCharVector, java.time.ZonedDateTime]   =
    codec[VarCharVector, java.time.ZonedDateTime]

  implicit def listCodec[A, C[_]](implicit
    encoder: ValueVectorEncoder[ListVector, C[A]],
    decoder: ValueVectorDecoder[ListVector, C[A]]
  ): ValueVectorCodec[ListVector, C[A]] =
    codec[ListVector, C[A]]

  implicit def listChunkCodec[A](implicit
    encoder: ValueVectorEncoder[ListVector, Chunk[A]],
    decoder: ValueVectorDecoder[ListVector, Chunk[A]]
  ): ValueVectorCodec[ListVector, Chunk[A]] =
    listCodec[A, Chunk]

  implicit def listOptionCodec[A, C[_]](implicit
    encoder: ValueVectorEncoder[ListVector, C[Option[A]]],
    decoder: ValueVectorDecoder[ListVector, C[Option[A]]]
  ): ValueVectorCodec[ListVector, C[Option[A]]] =
    listCodec[Option[A], C]

  implicit def listChunkOptionCodec[A](implicit
    encoder: ValueVectorEncoder[ListVector, Chunk[Option[A]]],
    decoder: ValueVectorDecoder[ListVector, Chunk[Option[A]]]
  ): ValueVectorCodec[ListVector, Chunk[Option[A]]] =
    listChunkCodec[Option[A]]

  implicit def structCodec[A](implicit
    encoder: ValueVectorEncoder[StructVector, A],
    decoder: ValueVectorDecoder[StructVector, A]
  ): ValueVectorCodec[StructVector, A] =
    codec[StructVector, A]

  implicit def optionCodec[V <: ValueVector, A](implicit
    encoder: ValueVectorEncoder[V, Option[A]],
    decoder: ValueVectorDecoder[V, Option[A]]
  ): ValueVectorCodec[V, Option[A]] =
    codec[V, Option[A]]

  implicit def optionListCodec[A, C[_]](implicit
    encoder: ValueVectorEncoder[ListVector, Option[C[A]]],
    decoder: ValueVectorDecoder[ListVector, Option[C[A]]]
  ): ValueVectorCodec[ListVector, Option[C[A]]] =
    optionCodec[ListVector, C[A]]

  implicit def optionListChunkCodec[A](implicit
    encoder: ValueVectorEncoder[ListVector, Option[Chunk[A]]],
    decoder: ValueVectorDecoder[ListVector, Option[Chunk[A]]]
  ): ValueVectorCodec[ListVector, Option[Chunk[A]]] =
    optionListCodec[A, Chunk]

}
