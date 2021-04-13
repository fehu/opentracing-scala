package com.github.fehu.opentracing.propagation

import io.opentracing.propagation.Format

trait Propagation {
  type Underlying
  def underlying: Underlying

  def format: Format[Underlying]

  type Repr
  def repr: Repr
}

trait PropagationCompanion[C <: Propagation] {
  def apply(): C
  def apply(repr: C#Repr): C

  def format: Format[C#Underlying]

  final implicit def companion: PropagationCompanion[C] = this
}
