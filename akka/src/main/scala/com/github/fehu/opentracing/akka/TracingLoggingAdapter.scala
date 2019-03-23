package com.github.fehu.opentracing.akka

import akka.event.LoggingAdapter
import io.opentracing.Span

import com.github.fehu.opentracing.{ SpanOps, util }

trait TracingLoggingAdapter extends LoggingAdapter {
  protected def span: Span

  abstract override protected def notifyError(message: String): Unit = {
    super.notifyError(message)
    traceLog("Error", message)
  }
  abstract override protected def notifyError(cause: Throwable, message: String): Unit = {
    super.notifyError(cause, message)
    traceLog("Error", message, "cause" -> cause)
  }
  abstract override protected def notifyWarning(message: String): Unit = {
    super.notifyWarning(message)
    traceLog("Warning", message)
  }
  abstract override protected def notifyInfo(message: String): Unit = {
    super.notifyInfo(message)
    traceLog("Info", message)
  }
  abstract override protected def notifyDebug(message: String): Unit = {
    super.notifyDebug(message)
    traceLog("Debug", message)
  }

  def traceLog(level: String, message: String, fields: (String, Any)*): Unit =
    util.safe(span)(_
      .log(fields ++ Seq(
        "level" -> level,
        "message" -> message
      ): _*)
    )
}
