package com.github.fehu.opentracing.akka

import akka.event.LoggingAdapter
import com.github.fehu.opentracing.SpanLog
import io.opentracing.Span


trait TracingLoggingAdapter extends LoggingAdapter {
  protected def span: Span
  lazy val spanLog = new SpanLog(span)

  abstract override protected def notifyError(message: String): Unit = {
    super.notifyError(message)
    spanLog.error(message)
  }
  abstract override protected def notifyError(cause: Throwable, message: String): Unit = {
    super.notifyError(cause, message)
    spanLog.error(cause, message)
  }
  abstract override protected def notifyWarning(message: String): Unit = {
    super.notifyWarning(message)
    spanLog.warn(message)
  }
  abstract override protected def notifyInfo(message: String): Unit = {
    super.notifyInfo(message)
    spanLog.info(message)
  }
  abstract override protected def notifyDebug(message: String): Unit = {
    super.notifyDebug(message)
    spanLog.debug(message)
  }
}
