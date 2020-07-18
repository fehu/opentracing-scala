package com.github.fehu.opentracing.v2.internal

import scala.jdk.CollectionConverters._

import cats.~>
import cats.effect.Sync
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.opentracing.{ Span, SpanContext, Tracer }

import com.github.fehu.opentracing.v2.Traced

private[opentracing] case class State(
  private[opentracing] val tracer: Tracer,
  private[opentracing] val hooks: Traced.Hooks,
  private[opentracing] val currentSpan: Option[Span]
)

private[opentracing] class CurrentSpan[F[_]](private[opentracing] val fOpt: F[Option[Span]])(implicit sync: Sync[F])
  extends Traced.SpanInterface[F]
{ self =>

  private def delay[R](f: Span => R): F[Option[R]] = fOpt.flatMap(_.traverse(span => sync.delay(f(span))))

  def context: F[Option[SpanContext]] = delay(_.context())

  def setTag(tag: Traced.Tag): F[Unit] = delay(tag.apply(_)).void
  def setTags(tags: Traced.Tag*): F[Unit] =
    if (tags.nonEmpty) delay(tags.foldLeft(_)((s, t) => t.apply(s))).void
    else sync.unit

  def log(fields: (String, Any)*): F[Unit] = if (fields.nonEmpty) delay(_.log(fields.toMap.asJava)).void else sync.unit

  def log(event: String): F[Unit] = delay(_.log(event)).void

  def setBaggageItem(key: String, value: String): F[Unit] = delay(_.setBaggageItem(key, value)).void

  def getBaggageItem(key: String): F[Option[String]] = delay(_.getBaggageItem(key))

  def mapK[G[_]](f: F ~> G): Traced.SpanInterface[G] = new Traced.SpanInterface[G] {
    def context: G[Option[SpanContext]] = f(self.context)
    def setTag(tag: Traced.Tag): G[Unit] = f(self.setTag(tag))
    def setTags(tags: Traced.Tag*): G[Unit] = f(self.setTags(tags: _*))
    def log(fields: (String, Any)*): G[Unit] = f(self.log(fields: _*))
    def log(event: String): G[Unit] = f(self.log(event))
    def setBaggageItem(key: String, value: String): G[Unit] = f(self.setBaggageItem(key, value))
    def getBaggageItem(key: String): G[Option[String]] = f(self.getBaggageItem(key))
    def mapK[H[_]](g: G ~> H): Traced.SpanInterface[H] = self.mapK(g compose f)
    def noop: G[Unit] = f(sync.unit)
  }

  def noop: F[Unit] = sync.unit
}

private[opentracing] object CurrentSpan {
  def apply[F[_]: Sync](span: F[Span]): CurrentSpan[F] = new CurrentSpan(span.map(Option(_)))
  def apply[F[_]: Sync](span: Span): CurrentSpan[F] = new CurrentSpan(Sync[F].pure(Option(span)))
  def apply[F[_]: Sync](span: Option[Span]): CurrentSpan[F] = new CurrentSpan(Sync[F].pure(span))
}
