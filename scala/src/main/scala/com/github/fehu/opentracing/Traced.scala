package com.github.fehu.opentracing

import scala.language.{ existentials, implicitConversions }

import cats.~>
import cats.effect.Resource
import io.opentracing.propagation.Format
import io.opentracing.{ Span, SpanContext, Tracer, tag }

import com.github.fehu.opentracing.util.FunctionK2.~~>

trait Traced2[F[_[*], _], U[_]] extends Traced[F[U, *]] {
  def currentRunParams: F[U, Traced.RunParams]
  def run[A](traced: F[U, A], params: Traced.RunParams): U[A]

  def lift[A](ua: U[A]): F[U, A]
  def mapK[G[_]](f: U ~> G): F[U, *] ~> F[G, *]
}

trait Traced[F[_]] extends Traced.Interface[F] {
  def pure[A](a: A): F[A]
  def defer[A](fa: => F[A]): F[A]

  def currentSpan: Traced.SpanInterface[F]

  def forceCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]
  /** Sets `active` span if no other is set. */
  def recoverCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]

  def injectContext(context: SpanContext): Traced.Interface[F]
  def injectContextFrom[C](format: Format[C])(carrier: C): Traced.Interface[F]

  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): F[Option[C0]]
}

object Traced {
  def apply[F[_]](implicit traced: Traced[F]): Traced[F] = traced

  trait Interface[F[_]] {
    def apply[A](op: String, tags: Traced.Tag*)(fa: F[A]): F[A]
    def spanResource(op: String, tags: Traced.Tag*): Resource[F, ActiveSpan]
  }

  class Tag(val apply: Taggable.PartiallyApplied) extends AnyVal

  object Tag {
    implicit def stringPair[A](p: (String, A))(implicit t: Taggable[A]): Tag = new Tag(t(p._1, p._2))
    implicit def tagPair[A](p: (tag.Tag[A], A))(implicit t: Taggable[A]): Tag = new Tag(t(p._1.getKey, p._2))
  }

  trait Taggable[A] { self =>
    def apply(builder: Tracer.SpanBuilder, key: String, value: A): Tracer.SpanBuilder
    def apply(builder: Span, key: String, value: A): Span

    def apply(key: String, value: A): Taggable.PartiallyApplied =
      new Taggable.PartiallyApplied {
        def apply(builder: Tracer.SpanBuilder): Tracer.SpanBuilder = self(builder, key, value)
        def apply(builder: Span): Span = self(builder, key, value)
      }

    def contramap[B](f: B => A): Taggable[B] =
      new Taggable[B] {
        def apply(builder: Tracer.SpanBuilder, key: String, value: B): Tracer.SpanBuilder = self(builder, key, f(value))
        def apply(builder: Span, key: String, value: B): Span = self(builder, key, f(value))
      }
  }

  object Taggable {
    trait PartiallyApplied {
      def apply(builder: Tracer.SpanBuilder): Tracer.SpanBuilder
      def apply(builder: Span): Span
    }

    implicit lazy val stringIsTaggable: Taggable[String] =
      new Taggable[String] {
        def apply(builder: Tracer.SpanBuilder, key: String, value: String): Tracer.SpanBuilder = builder.withTag(key, value)
        def apply(builder: Span, key: String, value: String): Span = builder.setTag(key, value)
      }
    implicit lazy val boolIsTaggable: Taggable[Boolean] =
      new Taggable[Boolean] {
        def apply(builder: Tracer.SpanBuilder, key: String, value: Boolean): Tracer.SpanBuilder = builder.withTag(key, value)
        def apply(builder: Span, key: String, value: Boolean): Span = builder.setTag(key, value)
      }
    implicit lazy val numberIsTaggable: Taggable[Number] = new Taggable[Number] {
      def apply(builder: Tracer.SpanBuilder, key: String, value: Number): Tracer.SpanBuilder = builder.withTag(key, value)
      def apply(builder: Span, key: String, value: Number): Span = builder.setTag(key, value)
    }
    implicit lazy val intIsTaggable: Taggable[Int]       = numberIsTaggable.contramap(Int.box)
    implicit lazy val longIsTaggable: Taggable[Long]     = numberIsTaggable.contramap(Long.box)
    implicit lazy val doubleIsTaggable: Taggable[Double] = numberIsTaggable.contramap(Double.box)
    implicit lazy val floatIsTaggable: Taggable[Float]   = numberIsTaggable.contramap(Float.box)
  }

  trait SpanInterface[F[_]] {
    def context: F[Option[SpanContext]]

    def setTag(tag: Traced.Tag): F[Unit]
    def setTags(tags: Traced.Tag*): F[Unit]

    def log(fields: (String, Any)*): F[Unit]
    def log(event: String): F[Unit]

    def setBaggageItem(key: String, value: String): F[Unit]
    def getBaggageItem(key: String): F[Option[String]]

    def mapK[G[_]](f: F ~> G): SpanInterface[G]
    def noop: F[Unit]
  }

  class AccumulativeSpanInterface[F[_]](i: SpanInterface[F], accRev: List[SpanInterface[F] => F[Unit]]) {
    def setTag(tag: Traced.Tag): AccumulativeSpanInterface[F] = accumulate(_.setTag(tag))
    def setTags(tags: Traced.Tag*): AccumulativeSpanInterface[F] = accumulate(_.setTags(tags: _*))

    def log(fields: (String, Any)*): AccumulativeSpanInterface[F] = accumulate(_.log(fields: _*))
    def log(event: String): AccumulativeSpanInterface[F] = accumulate(_.log(event))

    def noop: AccumulativeSpanInterface[F] = this

    def accumulated: List[SpanInterface[F] => F[Unit]] = accRev.reverse

    private def accumulate(f: SpanInterface[F] => F[Unit]) = new AccumulativeSpanInterface[F](i, f :: accRev)
  }

  trait SpanInterfaceK2 {
    def setTag(tag: Traced.Tag): AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.setTag(tag)
      }

    def setTags(tags: Traced.Tag*): AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.setTags(tags: _*)
      }

    def log(fields: (String, Any)*): AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.log(fields: _*)
      }

    def log(event: String): AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.log(event)
      }

    def noop: AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.noop
      }
  }
  object SpanInterfaceK2 extends SpanInterfaceK2

  final case class RunParams(tracer: Tracer, hooks: Hooks, activeSpan: ActiveSpan)

  object RunParams {
    def apply(tracer: Tracer, hooks: Hooks): Partial = Partial(tracer, hooks)

    final case class Partial(tracer: Tracer, hooks: Hooks) {
      def apply(active: ActiveSpan): RunParams = RunParams(tracer, hooks, active)
    }
    implicit def fromPartial(p: Partial)(implicit active: ActiveSpan): RunParams = p(active)
  }

  final class ActiveSpan(val maybe: Option[Span]) extends AnyVal {
    override def toString: String = s"ActiveSpan(${maybe.toString})"
  }

  object ActiveSpan {
    def apply(span: Option[Span]): ActiveSpan = new ActiveSpan(span)
    def apply(span: Span): ActiveSpan = apply(Option(span))

    lazy val empty: ActiveSpan = new ActiveSpan(None)

    object Implicits {
      implicit val emptyActiveSpan: ActiveSpan = empty
    }
  }

  final class Hooks(
    val beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder,
    val justAfterStart: SpanInterface ~~> λ[F[_] => List[SpanInterface[F] => F[Unit]]],
    val beforeStop: SpanInterface ~~> λ[F[_] => Option[Throwable] => List[SpanInterface[F] => F[Unit]]]
  )

  object Hooks {
    def apply(
      beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder = null,
      justAfterStart: SpanInterfaceK2 => (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) = null,
      beforeStop: SpanInterfaceK2 => Option[Throwable] => (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) = null
    ): Hooks = {
      val justAfterStart1 = Option(justAfterStart).getOrElse((_: SpanInterfaceK2).noop).apply(SpanInterfaceK2)
      val beforeStop1 = Option(beforeStop).getOrElse((s: SpanInterfaceK2) => (_: Option[Throwable]) => s.noop).apply(SpanInterfaceK2)

      new Hooks(
        Option(beforeStart).getOrElse(locally),
        accumulateK2 compose justAfterStart1 compose accumulativeSpanInterfaceK2,
        new (SpanInterface ~~> λ[F[_] => Option[Throwable] => List[SpanInterface[F] => F[Unit]]]) {
          def apply[A[_]](fa: SpanInterface[A]): Option[Throwable] => List[SpanInterface[A] => A[Unit]] =
            e => beforeStop1(e)(new AccumulativeSpanInterface(fa, Nil)).accumulated
        }
      )
    }

    lazy val accumulativeSpanInterfaceK2: SpanInterface ~~> AccumulativeSpanInterface =
      new (SpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: SpanInterface[A]): AccumulativeSpanInterface[A] = new AccumulativeSpanInterface(fa, Nil)
      }

    lazy val accumulateK2: AccumulativeSpanInterface ~~> λ[F[_] => List[SpanInterface[F] => F[Unit]]] =
      new (AccumulativeSpanInterface ~~> λ[F[_] => List[SpanInterface[F] => F[Unit]]]) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): List[SpanInterface[A] => A[Unit]] = fa.accumulated
      }
  }
}
