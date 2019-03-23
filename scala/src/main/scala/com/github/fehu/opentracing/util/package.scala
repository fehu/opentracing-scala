package com.github.fehu.opentracing

import io.opentracing.{ Scope, Span }

package object util {
  def finishSpanSafe(span: Span): Unit = safe(span)(_.finish())

  def closeScopeSafe(scope: Scope): Unit =
    if (scope ne null) {
      try scope.close()
      catch { case _: IllegalStateException => }
    }

  def safe(span: Span)(f: Span => Unit): Unit =
    if (span ne null) {
      try f(span)
      catch { case _: IllegalStateException => }
    }

}
