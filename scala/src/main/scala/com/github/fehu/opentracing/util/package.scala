package com.github.fehu.opentracing

import io.opentracing.{ Scope, Span }

package object util {
  def finishSpanSafe(span: Span): Unit =
    if (span ne null) {
      try span.finish()
      catch { case _: IllegalStateException => }
    }

  def closeScopeSafe(scope: Scope): Unit =
    if (scope ne null) {
      try scope.close()
      catch { case _: IllegalStateException => }
    }
}
