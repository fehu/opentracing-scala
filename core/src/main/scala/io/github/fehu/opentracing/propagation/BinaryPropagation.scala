package io.github.fehu.opentracing.propagation

import java.nio.ByteBuffer

import io.opentracing.propagation.{ Binary, Format }

import io.github.fehu.opentracing.internal.compat.*

object BinaryPropagation extends Propagation {
  type Underlying = Binary
  type Repr       = Array[Byte]

  def format: Format[Binary] = Format.Builtin.BINARY.nn

  def apply(): Carrier                   = new CarrierImpl(ByteBuffer.allocate(0).nn)
  def apply(bytes: Array[Byte]): Carrier = new CarrierImpl(ByteBuffer.wrap(bytes).nn)

  private class CarrierImpl(private var buff: ByteBuffer) extends Carrier {
    def underlying: Binary = new Binary {
      def extractionBuffer(): ByteBuffer = buff

      def injectionBuffer(length: Int): ByteBuffer = {
        buff = ByteBuffer.allocate(length).nn
        buff
      }
    }
    def repr: Array[Byte] = buff.array().nn
  }
}
