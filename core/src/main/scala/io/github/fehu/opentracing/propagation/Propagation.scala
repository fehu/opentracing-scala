package io.github.fehu.opentracing.propagation

import io.opentracing.propagation.Format

trait Propagation { prop =>
  type Underlying
  type Repr

  def apply(): Carrier
  def apply(repr: Repr): Carrier

  def format: Format[Underlying]

  trait Carrier {
    type Underlying = prop.Underlying

    def underlying: Underlying
    def repr: Repr

    final def format: Format[Underlying] = prop.format
    final def propagation: prop.type     = prop
  }
}
