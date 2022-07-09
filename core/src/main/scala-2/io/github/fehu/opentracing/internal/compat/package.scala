package io.github.fehu.opentracing.internal

package object compat {
  implicit final class NNOps[A](private val a: A) extends AnyVal {
    @inline def nn: A = a
  }
}
