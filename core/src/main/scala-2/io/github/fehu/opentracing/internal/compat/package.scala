package io.github.fehu.opentracing.internal

package object compat {
  final implicit class NNOps[A](private val a: A) extends AnyVal {
    @inline def nn: A = a
  }
}
