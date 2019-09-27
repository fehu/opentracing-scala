package com.github.fehu.opentracing.akka

import akka.fehu.MessageInterceptingActor
import com.github.fehu.opentracing.{ Tracing, util }
import io.opentracing.{ Span, Tracer }


final case class TracedMessage[A](message: A, span: Span)


trait TracingActor extends MessageInterceptingActor {
  implicit val tracer: Tracer

  def actorSpan(): Option[Span] = _span
  private var _span: Option[Span] = None

  protected[TracingActor] def setSpan(span: Span): Unit = _span = Option(span)

  protected def onSpanReceived(message: Any, span: Span): Unit = {}
  protected def onNoSpanReceived(message: Any): Unit = {}

  protected def interceptIncoming(message: Any): Any = message match {
    case TracedMessage(msg, span) if span ne null =>
      _span = Some(span)
      onSpanReceived(msg, span)
      msg
    case TracedMessage(msg, _) =>
      onNoSpanReceived(msg)
      msg
    case _ =>
      onNoSpanReceived(message)
      message
  }

  protected def afterReceive(maybeError: Option[Throwable]): Unit = {
    _span = None
  }
}


object TracingActor {

  trait Activating extends TracingActor {
    def finishSpanOnClose: Boolean = true

    def activeSpan(): Option[Span] = Option(tracer.activeSpan())

    override protected def onSpanReceived(message: Any, span: Span): Unit = {
      super.onSpanReceived(message, span)
      activate(span)
    }

    override protected def onNoSpanReceived(message: Any): Unit = {
      super.onNoSpanReceived(message)
      actorSpan().foreach(activate)
    }

    override protected def afterReceive(maybeError: Option[Throwable]): Unit = {
      maybeError.foreach(err => util
        .safe(actorSpan().orNull)(_
          .setTag("error", true)
          .setTag("error.message", err.getMessage)
        )
      )
      val activeScope = tracer.scopeManager().active()
      val sameSpan = actorSpan() == Option(activeScope).map(_.span())
      util.finishSpanSafe(actorSpan().orNull)
      if (!sameSpan) util.closeScopeSafe(activeScope)
      super.afterReceive(maybeError)
    }

    private def activate(span: Span) = tracer.scopeManager().activate(span, finishSpanOnClose)
  }

  // The order of inheritance is important!
  trait ActivatingChildSpan extends Activating with ChildSpan

  trait ChildSpan extends TracingActor {
    actor =>

    def buildChildSpan(message: Any): Tracer.SpanBuilder

    object buildSpan extends Tracing.Interface[Tracer.SpanBuilder] {
      val tracer: Tracer = actor.tracer

      // `activate` is ignored
      def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, Tracing.TagValue]): Tracer.SpanBuilder =
        Tracing.Interface.impl(parent, activate, operation, tags)(tracer, (b, _) => b, sys.error("tracer is null"))
    }

    override protected def onSpanReceived(message: Any, span: Span): Unit = {
      val childSpan = buildChildSpan(message).asChildOf(span).start()
      super.onSpanReceived(message, childSpan)
    }
  }

  trait AlwaysChildSpan extends ChildSpan {
    override protected def onNoSpanReceived(message: Any): Unit = {
      val span = buildChildSpan(message).start()
      setSpan(span)
      super.onNoSpanReceived(message)
    }
  }

}