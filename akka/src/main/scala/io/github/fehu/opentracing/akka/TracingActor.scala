package io.github.fehu.opentracing.akka

import akka.fehu.MessageInterceptingActor
import io.opentracing.{ Span, SpanContext, Tracer }

import io.github.fehu.opentracing.internal.compat.*
import io.github.fehu.opentracing.Traced

final case class TracedMessage[A](message: A, spanContext: Option[SpanContext])

trait TracingActor extends MessageInterceptingActor {
  implicit val tracer: Tracer

  def actorSpanContext(): Option[SpanContext] = _spanContext
  private var _spanContext: Option[SpanContext] = None

  protected[TracingActor] def setSpanContext(ctx: SpanContext): Unit = _spanContext = Option(ctx)

  protected def onSpanReceived(message: Any, ctx: SpanContext): Unit = setSpanContext(ctx)
  protected def onNoSpanReceived(message: Any): Unit = {}

  protected def interceptIncoming(message: Any): Any = message match {
    case TracedMessage(msg, Some(ctx)) =>
      _spanContext = Some(ctx)
      onSpanReceived(msg, ctx)
      msg
    case TracedMessage(msg, _) =>
      onNoSpanReceived(msg)
      msg
    case _ =>
      onNoSpanReceived(message)
      message
  }

  protected def afterReceive(maybeError: Option[Throwable]): Unit = {
    _spanContext = None
  }
}

object TracingActor {

  trait ChildSpan extends TracingActor {
    actor =>

    def buildChildSpan(message: Any): Tracer.SpanBuilder

    def actorSpan(): Option[Span] = _span
    private var _span: Option[Span] = None

    object buildSpan {
      def apply[A](op: String, tags: Traced.Tag*): Tracer.SpanBuilder = {
        val b0 = tracer.buildSpan(op).nn.ignoreActiveSpan.nn
        tags.foldLeft(b0)((b, t) => t.apply(b))
      }
    }

    override protected def onSpanReceived(message: Any, ctx: SpanContext): Unit = {
      _span = Some(buildChildSpan(message).asChildOf(ctx).nn.start().nn)
      super.onSpanReceived(message, ctx)
    }

    def modifySpanOnError(span: Span): Span = span

    override protected def afterReceive(maybeError: Option[Throwable]): Unit = {
      try _span.map(modifySpanOnError).foreach(_.finish())
      finally super.afterReceive(maybeError)
    }
  }

  trait AlwaysChildSpan extends ChildSpan {
    override protected def onNoSpanReceived(message: Any): Unit = {
      val span = buildChildSpan(message).start().nn
      setSpanContext(span.context().nn)
      super.onNoSpanReceived(message)
    }
  }

}