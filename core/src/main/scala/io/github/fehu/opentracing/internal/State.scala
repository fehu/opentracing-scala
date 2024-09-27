package io.github.fehu.opentracing.internal

import scala.collection.JavaConverters.*

import cats.effect.Sync
import cats.instances.option.*
import cats.syntax.applicative.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.traverse.*
import cats.~>
import io.opentracing.{ Span, SpanContext, Tracer }

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.internal.compat.*
import io.github.fehu.opentracing.util.ErrorLogger

final case class State(
    private[opentracing] val setup: Traced.Setup,
    private[opentracing] val currentSpan: Option[Span]
) {
  @inline private[opentracing] def tracer: Tracer        = setup.tracer
  @inline private[opentracing] def hooks: Traced.Hooks   = setup.hooks
  @inline private[opentracing] def logError: ErrorLogger = setup.logError

  @inline def activeSpan: Traced.ActiveSpan = Traced.ActiveSpan(currentSpan)
  @inline def toRunParams: Traced.RunParams = Traced.RunParams(setup, activeSpan)
}

object State {
  def fromRunParams(params: Traced.RunParams): State = State(params.setup, params.activeSpan.maybe)
}

private[opentracing] class CurrentSpan[F[_]](private[opentracing] val fOpt: F[Option[Span]])(implicit sync: Sync[F])
    extends Traced.SpanInterface[F] { self =>

  private def delay[R](f: Span => R): F[Option[R]] = fOpt.flatMap(_.traverse(span => sync.delay(f(span))))

  def context: F[Option[SpanContext]] = delay(_.context().nn)

  def setOperation(op: String): F[Unit] = delay(_.setOperationName(op)).void

  def setTag(tag: Traced.Tag): F[Unit] = delay(tag.apply(_)).void
  def setTags(tags: Traced.Tag*): F[Unit] =
    if (tags.nonEmpty) delay(tags.foldLeft(_)((s, t) => t.apply(s))).void
    else sync.unit

  def log(fields: (String, Any)*): F[Unit] = if (fields.nonEmpty) delay(_.log(fields.toMap.asJava)).void else sync.unit

  def log(event: String): F[Unit] = delay(_.log(event)).void

  def setBaggageItem(key: String, value: String): F[Unit] = delay(_.setBaggageItem(key, value)).void

  def getBaggageItem(key: String): F[Option[String]] = delay(_.getBaggageItem(key).nn)

  def mapK[G[_]](f: F ~> G): Traced.SpanInterface[G] = new Traced.SpanInterface[G] {
    def context: G[Option[SpanContext]]                     = f(self.context)
    def setOperation(op: String): G[Unit]                   = f(self.setOperation(op))
    def setTag(tag: Traced.Tag): G[Unit]                    = f(self.setTag(tag))
    def setTags(tags: Traced.Tag*): G[Unit]                 = f(self.setTags(tags*))
    def log(fields: (String, Any)*): G[Unit]                = f(self.log(fields*))
    def log(event: String): G[Unit]                         = f(self.log(event))
    def setBaggageItem(key: String, value: String): G[Unit] = f(self.setBaggageItem(key, value))
    def getBaggageItem(key: String): G[Option[String]]      = f(self.getBaggageItem(key))
    def mapK[H[_]](g: G ~> H): Traced.SpanInterface[H]      = self.mapK(g compose f)
    def noop: G[Unit]                                       = f(sync.unit)
  }

  def noop: F[Unit] = sync.unit
}

private[opentracing] object CurrentSpan {
  def apply[F[_]: Sync](span: F[Span]): CurrentSpan[F]      = new CurrentSpan(span.map(Option(_)))
  def apply[F[_]: Sync](span: Span): CurrentSpan[F]         = new CurrentSpan(Option(span).pure[F])
  def apply[F[_]: Sync](span: Option[Span]): CurrentSpan[F] = new CurrentSpan(span.pure[F])
}
