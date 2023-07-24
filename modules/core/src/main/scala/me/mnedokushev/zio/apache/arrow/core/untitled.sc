import me.mnedokushev.zio.apache.arrow.core.codec.{ ArrowVectorDecoder, ArrowVectorEncoder }
import me.mnedokushev.zio.apache.arrow.core.ArrowAllocator
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.IntVector
import org.apache.arrow.vector.complex.StructVector
import org.apache.arrow.vector.complex.impl.{ IntReaderImpl, UnionListReader }
import org.apache.arrow.vector.types.pojo.{ ArrowType, FieldType }
import zio._
import zio.schema._

import scala.collection.mutable.ListBuffer

// Fixtures

final case class ListOfScalars(list: List[Int])
object ListOfScalars {
  implicit val schema: Schema[ListOfScalars] = DeriveSchema.gen[ListOfScalars]
}

final case class StructOfScalars(a: Int, b: String)
object StructOfScalars {
  implicit val schema: Schema[StructOfScalars] = DeriveSchema.gen[StructOfScalars]
}

// Allocator

implicit val alloc = new RootAllocator()

// Write

// Struct
//val structVec = StructVector.empty("structVector", alloc)
//
//val writer                   = structVec.getWriter
//val writerListFoo            = writer.list("foo")
//val writerIntBar             = writer.integer("bar")
//val writerStructBaz          = writer.struct("baz")
//val writerStructBazListC     = writerStructBaz.list("c")
//val writerListDog            = writer.list("dog")
//val writerListDogStruct      = writerListDog.struct()
//val writerListDogStructListB = writerListDogStruct.list("b")
//
////writer.setInitialCapacity(16)
//
////writer.allocate()
////writer.start()
//
//writer.setPosition(0)
//writerListFoo.startList()
//writerListFoo.integer().writeInt(16)
//writerListFoo.integer().writeInt(24)
//writerListFoo.integer().writeInt(12)
//writerListFoo.integer().writeInt(12314)
//writerListFoo.endList()
//writerIntBar.writeInt(12)
//writerStructBaz.start()
//writerStructBaz.integer("a").writeInt(231)
//writerStructBaz.bit("b").writeBit(1)
//writerStructBaz.end()
//writerStructBazListC.setPosition(0)
//writerStructBazListC.startList()
//writerStructBazListC.integer().writeInt(123)
//writerStructBazListC.integer().writeInt(12341)
//writerStructBazListC.endList()
//writerListDog.startList()
//writerListDogStruct.start()
//writerListDogStruct.integer("a").writeInt(444)
//writerListDogStructListB.setPosition(0)
//writerListDogStructListB.startList()
//writerListDogStructListB.integer().writeInt(123)
//writerListDogStructListB.integer().writeInt(1321231)
//writerListDogStructListB.endList()
//writerListDogStruct.end()
//writerListDog.endList()
//structVec.setIndexDefined(0)
//
//writer.setPosition(1)
//writerListFoo.startList()
//writerListFoo.integer().writeInt(16123)
//writerListFoo.endList()
//writerIntBar.writeInt(123)
//writerStructBaz.start()
//writerStructBaz.integer("a").writeInt(1111)
//writerStructBaz.bit("b").writeBit(0)
//writerStructBaz.end()
//writerStructBazListC.setPosition(1)
//writerStructBazListC.startList()
//writerStructBazListC.integer().writeInt(1)
//writerStructBazListC.integer().writeInt(3333)
//writerStructBazListC.endList()
//writerListDog.startList()
//writerListDogStruct.start()
//writerListDogStruct.integer("a").writeInt(9999)
//writerListDogStructListB.setPosition(1)
//writerListDogStructListB.startList()
//writerListDogStructListB.integer().writeInt(888828)
//writerListDogStructListB.integer().writeInt(6354512)
//writerListDogStructListB.endList()
//writerListDogStruct.end()
//writerListDog.endList()
//structVec.setIndexDefined(1)
//
////writer.end()
//writer.setValueCount(2)
//
//structVec

// Read

// Struct
implicit val dataStructEncoder = ArrowVectorEncoder.struct[ListOfScalars]

val structVec = ArrowVectorEncoder[ListOfScalars, StructVector]
  .encode(Chunk(ListOfScalars(List(1, 2, 3)), ListOfScalars(List(2))))
  .getOrElse(StructVector.empty("struct", alloc))

val reader  = structVec.getReader
val reader0 = reader.reader("list").asInstanceOf[UnionListReader]
val reader1 = reader0.reader()

reader0.setPosition(0)
val listBuffer1 = ListBuffer.empty[Int]
while (reader0.next())
  if (reader0.isSet)
    listBuffer1.addOne(reader1.readInteger())
listBuffer1.result()

reader.setPosition(1)
val listBuffer2 = ListBuffer.empty[Int]
while (reader0.next())
  if (reader.isSet)
    listBuffer2.addOne(reader1.readInteger())
listBuffer2.result()

//val vec = ZVector.Int.Unsafe(Seq(1, 2, 3))
//ArrowVectorDecoder.intDecoder.decode(vec)
//
//val emptyVec = ZVector.Int.Unsafe.empty
//ArrowVectorDecoder.intDecoder.decode(emptyVec)
//
////
////val prog = for {
////  vec <- ZVector.Int(1, 2, 3)
////  dec <- ZIO.fromEither(VectorDecoder.intDecoder.decode(vec))
////} yield dec
////
////prog.provideLayer(ZAllocator.rootLayer())
//
//case class Person(name: String, age: Int, balance: Long)
//
//implicit val schemaPerson: Schema.CaseClass3[String, Int, Long, Person] =
//  Schema.CaseClass3[String, Int, Long, Person](
//    TypeId.Structural,
//    Schema
//      .Field[Person, String]("name", Schema.primitive[String], get0 = _.name, set0 = (r, name) => r.copy(name = name)),
//    Schema.Field[Person, Int]("age", Schema.primitive[Int], get0 = _.age, set0 = (r, age) => r.copy(age = age)),
//    Schema.Field[Person, Long](
//      "balance",
//      Schema.primitive[Long],
//      get0 = _.balance,
//      set0 = (r, balance) => r.copy(balance = balance)
//    ),
//    Person
//  )
//
//val emptyStructVec = ZVector.Struct[Person].Unsafe.empty
//ArrowVectorDecoder.struct[Person].decode(emptyStructVec)
