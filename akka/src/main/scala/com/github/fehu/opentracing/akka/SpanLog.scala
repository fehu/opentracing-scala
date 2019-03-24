package com.github.fehu.opentracing.akka

import com.github.fehu.opentracing.{ SpanOps, util }
import io.opentracing.Span

class SpanLog(span: Span) {

  def error(err: Throwable, message: String, fields: (String, Any)*): Unit = SpanLog.error(span, err, message, fields: _*)
  def error(message: String, fields: (String, Any)*): Unit = SpanLog.error(span, message, fields: _*)
  def warn (message: String, fields: (String, Any)*): Unit = SpanLog.warn(span, message, fields: _*)
  def info (message: String, fields: (String, Any)*): Unit = SpanLog.info(span, message, fields: _*)
  def debug(message: String, fields: (String, Any)*): Unit = SpanLog.debug(span, message, fields: _*)

  def log(level: String, message: String, fields: (String, Any)*): Unit = SpanLog.log(span, level, message, fields: _*)
}

object SpanLog {
  def error(span: Span, err: Throwable, message: String, fields: (String, Any)*): Unit =
    log(span, "Error", message, ("error" -> err) +: fields: _*)
  def error(span: Span, message: String, fields: (String, Any)*): Unit = log(span, "Error", message, fields: _*)
  def warn (span: Span, message: String, fields: (String, Any)*): Unit = log(span, "Warning", message, fields: _*)
  def info (span: Span, message: String, fields: (String, Any)*): Unit = log(span, "Info", message, fields: _*)
  def debug(span: Span, message: String, fields: (String, Any)*): Unit = log(span, "Debug", message, fields: _*)

  def log(span: Span, level: String, message: String, fields: (String, Any)*): Unit =
    util.safe(span)(_
      .log(fields ++ Seq(
        "level" -> level,
        "message" -> message
      ): _*)
    )

}