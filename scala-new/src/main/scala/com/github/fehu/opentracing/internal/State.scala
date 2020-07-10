package com.github.fehu.opentracing.internal

import scala.jdk.CollectionConverters._

import cats.effect.Sync
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.opentracing.{ Span, SpanContext, Tracer }

import com.github.fehu.opentracing.Traced

private[opentracing] case class State[F[_]](
  private[opentracing] val tracer: Tracer,
  private[opentracing] val hooks: Traced.Hooks[F],
  private[opentracing] val currentSpan: Option[Span]
)

private[opentracing] class CurrentSpan[F[_]](private[opentracing] val option: Option[Span])(implicit sync: Sync[F])
  extends Traced.SpanInterface[F]
{
  private def delay[R](f: Span => R): F[Option[R]] = option.traverse(span => sync.delay(f(span)))

  def context: F[Option[SpanContext]] = delay(_.context())

  def setTag(tag: Traced.Tag): F[Unit] = delay(tag.apply(_)).void

  def log(field: (String, Any), fields: (String, Any)*): F[Unit] = delay(_.log((field +: fields).toMap.asJava)).void

  def setBaggageItem(key: String, value: String): F[Unit] = delay(_.setBaggageItem(key, value)).void

  def getBaggageItem(key: String): F[Option[String]] = delay(_.getBaggageItem(key))
}

private[opentracing] object CurrentSpan {
  def apply[F[_]: Sync](span: Span): CurrentSpan[F] = new CurrentSpan(Option(span))
}
