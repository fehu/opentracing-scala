package com.github.fehu.opentracing.propagation

import java.nio.ByteBuffer

import io.opentracing.propagation.{ Binary, Format }

final class BinaryPropagation private (private var buff: ByteBuffer) extends Propagation {
  type Underlying = Binary

  val underlying: Binary = new Binary {
    def extractionBuffer(): ByteBuffer = buff

    def injectionBuffer(length: Int): ByteBuffer = {
      buff = ByteBuffer.allocate(length)
      buff
    }
  }

  def format: Format[Binary] = BinaryPropagation.format

  type Repr = Array[Byte]
  def repr: Array[Byte] = buff.array()
}

object BinaryPropagation extends PropagationCompanion[BinaryPropagation] {
  def apply(): BinaryPropagation = new BinaryPropagation(ByteBuffer.allocate(0))
  def apply(bytes: Array[Byte]): BinaryPropagation = new BinaryPropagation(ByteBuffer.wrap(bytes))

  val format: Format[Binary] = Format.Builtin.BINARY
}
