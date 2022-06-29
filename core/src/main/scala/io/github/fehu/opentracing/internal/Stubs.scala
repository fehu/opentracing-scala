package io.github.fehu.opentracing.internal

import cats.effect.Resource
import cats.{Applicative, Defer, ~>}
import io.opentracing.SpanContext

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.internal.compat.FK
import io.github.fehu.opentracing.propagation.Propagation

protected[opentracing] class TracedStub[F[_]](implicit A: Applicative[F], D: Defer[F]) extends TracedInterfaceStub[F] with Traced[F] {
  def pure[A](a: A): F[A] = A.pure(a)
  def defer[A](fa: => F[A]): F[A] = D.defer(fa)
  def currentSpan: Traced.SpanInterface[F] = new SpanInterfaceStub(A.unit, FK.lift[cats.Id, F](A.pure))
  def forceCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]] = A.pure(currentSpan)
  def recoverCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]] = A.pure(currentSpan)
  def injectContext(context: SpanContext): Traced.Interface[F] = new TracedInterfaceStub[F]
  def injectContextFrom(carrier: Propagation#Carrier): Traced.Interface[F] = new TracedInterfaceStub[F]
  def extractContext[C <: Propagation#Carrier](carrier: C): F[Option[C]] = A.pure(None)
}

protected[opentracing] class TracedInterfaceStub[F[_]](implicit A: Applicative[F]) extends Traced.Interface[F] {
  def apply[A](op: String, tags: Traced.Tag*)(fa: F[A]): F[A] = fa
  def spanResource(op: String, tags: Traced.Tag*): Resource[F, Traced.ActiveSpan] = Resource.pure(Traced.ActiveSpan.empty)
  def withParent(span: Traced.ActiveSpan): Traced.Interface[F] = this
  def withParent(span: SpanContext): Traced.Interface[F] = this
  def withoutParent: Traced.Interface[F] = this
}

protected[opentracing] class SpanInterfaceStub[F[_]](unit: F[Unit], pure: cats.Id ~> F) extends Traced.SpanInterface[F] {
  def context: F[Option[SpanContext]] = pure(None)
  def setOperation(op: String): F[Unit] = unit
  def setTag(tag: Traced.Tag): F[Unit] = unit
  def setTags(tags: Traced.Tag*): F[Unit] = unit
  def log(fields: (String, Any)*): F[Unit] = unit
  def log(event: String): F[Unit] = unit
  def setBaggageItem(key: String, value: String): F[Unit] = unit
  def getBaggageItem(key: String): F[Option[String]] = pure(None)
  def noop: F[Unit] = unit
  def mapK[G[_]](f: F ~> G): Traced.SpanInterface[G] = new SpanInterfaceStub[G](f(unit), f compose pure)
}
