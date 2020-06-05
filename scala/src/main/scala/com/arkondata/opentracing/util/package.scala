package com.arkondata.opentracing

import io.opentracing.{ Scope, Span }

package object util {
  def finishSpanSafe(span: Span): Unit = safe(span)(_.finish())

  def closeScopeSafe(scope: Scope): Unit =
    if (scope ne null) {
      try scope.close()
      catch { case _: IllegalStateException => }
    }

  def safe[R](span: Span)(f: Span => R): Option[R] =
    if (span ne null) {
      try Option(f(span))
      catch { case _: IllegalStateException => None }
    }
    else None

}
