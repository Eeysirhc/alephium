package org.alephium

import akka.util.ByteString
import org.alephium.util.AVector

import scala.reflect.ClassTag

package object serde {
  import Serde._

  def serialize[T](input: T)(implicit serializer: Serde[T]): ByteString =
    serializer.serialize(input)

  def deserialize[T](input: ByteString)(implicit deserializer: Serde[T]): Either[SerdeError, T] =
    deserializer.deserialize(input)

  implicit val byteSerde: Serde[Byte] = ByteSerde

  implicit val intSerde: Serde[Int] = IntSerde

  implicit val longSerde: Serde[Long] = LongSerde

  implicit def avectorSerde[T: ClassTag](implicit serde: Serde[T]): Serde[AVector[T]] =
    dynamicSizeBytesSerde(serde)

  implicit val bigIntSerde: Serde[BigInt] =
    avectorSerde[Byte].xmap(vc => BigInt(vc.toArray), bi => AVector.unsafe(bi.toByteArray))
}