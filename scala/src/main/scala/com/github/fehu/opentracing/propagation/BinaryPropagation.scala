package com.github.fehu.opentracing.propagation

import java.nio.ByteBuffer

import io.opentracing.propagation.{ Binary, Format }

object BinaryPropagation extends Propagation {
  type Underlying = Binary
  type Repr = Array[Byte]

  def format: Format[Binary] = Format.Builtin.BINARY

  def apply(): Carrier = new CarrierImpl(ByteBuffer.allocate(0))
  def apply(bytes: Array[Byte]): Carrier = new CarrierImpl(ByteBuffer.wrap(bytes))

  private class CarrierImpl(private var buff: ByteBuffer) extends Carrier {
    def underlying: Binary = new Binary {
      def extractionBuffer(): ByteBuffer = buff

      def injectionBuffer(length: Int): ByteBuffer = {
        buff = ByteBuffer.allocate(length)
        buff
      }
    }
    def repr: Array[Byte] = buff.array()
  }
}
