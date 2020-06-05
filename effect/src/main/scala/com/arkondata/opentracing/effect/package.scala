package com.arkondata.opentracing

import cats.effect.{ Resource, Sync }
import cats.{ Applicative, Defer, Monad }
import cats.instances.list._
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.arkondata.opentracing.Tracing.{ TagValue, TracingSetup }
import com.arkondata.opentracing.util.cats.defer
import io.opentracing.{ Span, Tracer }

package object effect {

  def activeSpan[F[_]: Defer: Applicative](implicit tracer: Tracer): F[Option[Span]] =
    defer[F]{ Option(tracer.activeSpan()) }

  def activateSpan[F[_]: Defer: Applicative](span: Span)(implicit tracer: Tracer): F[Unit] =
    activateSpan(Option(span))

  def activateSpan[F[_]: Defer: Applicative](span: Option[Span])(implicit tracer: Tracer): F[Unit] =
    defer[F]{ span.foreach(tracer.activateSpan) }

  def addActiveSpanTags[F[_]: Defer: Monad](tag: Tracing.Tag, tags: Tracing.Tag*)(implicit tracer: Tracer): F[Unit] =
    for {
      span <- activeSpan
      _    <- span.traverse{ s => (tag :: tags.toList).traverse(t => defer{ setTag(s, t) }) }
    } yield ()

  implicit final class ResourceTracingOps[F[_]: Sync, A](r: Resource[F, A])(implicit tracer: Tracer, setup: TracingSetup) {
    def traceLifetime: Tracing.Interface[Resource[F, A]] = ResourceTracing.lifetimeTracing[F].apply(r)
    def traceCreation: Tracing.Interface[Resource[F, A]] = ResourceTracing.creationTracing[F].apply(r)
    def traceUsage:    Tracing.Interface[Resource[F, A]] = ResourceTracing.usageTracing[F].apply(r)
  }

  private def setTag(span: Span, tag: Tracing.Tag): Unit = tag.value match {
    case TagValue.String(s)  => span.setTag(tag.key, s)
    case TagValue.Number(n)  => span.setTag(tag.key, n)
    case TagValue.Boolean(b) => span.setTag(tag.key, b)
  }

}
