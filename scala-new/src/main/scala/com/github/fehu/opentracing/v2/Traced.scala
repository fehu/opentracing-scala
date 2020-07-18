package com.github.fehu.opentracing.v2

import scala.language.existentials

import cats.{ Applicative, Functor, ~> }
import cats.effect.Resource
import io.opentracing.propagation.Format
import io.opentracing.{ Span, SpanContext, Tracer, tag }

trait Traced2[F[_[*], _], U[_]] extends Traced[F[U, *]] {
  def currentRunParams: F[U, Traced.RunParams[U]]
  def run[A](traced: F[U, A], params: Traced.RunParams[U]): U[A]

  def lift[A](ua: U[A]): F[U, A]
  def imapK[G[_]: Functor](f: U ~> G, g: G ~> U): F[U, *] ~> F[G, *]
}

trait Traced[F[_]] extends Traced.Interface[F] {
  def pure[A](a: A): F[A]
  def defer[A](fa: => F[A]): F[A]

  def currentSpan: Traced.SpanInterface[F]

  def injectContext(context: SpanContext): Traced.Interface[F]
  def injectContextFrom[C](carrier: C, format: Format[C]): Traced.Interface[F]

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
  }

  final case class RunParams[F[_]](tracer: Tracer, hooks: Hooks[F], activeSpan: ActiveSpan)

  object RunParams {
    def apply[F[_]](tracer: Tracer, hooks: Hooks[F]): Partial[F] = Partial(tracer, hooks)

    final case class Partial[F[_]](tracer: Tracer, hooks: Hooks[F]) {
      def apply(active: ActiveSpan)(implicit A: Applicative[F]): RunParams[F] = RunParams(tracer, hooks, active)
    }
    implicit def fromPartial[F[_]: Applicative](p: Partial[F])(implicit active: ActiveSpan): RunParams[F] = p(active)
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

  final class Hooks[F[_]](
    val beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder,
    val justAfterStart: SpanInterface[F] => F[Unit],
    val beforeStop: SpanInterface[F] => Option[Throwable] => F[Unit]
  ) {
    def imapK[G[_]](f: F ~> G, g: G ~> F): Hooks[G] =
      new Hooks[G](
        beforeStart,
        justAfterStart compose ((_: SpanInterface[G]).mapK(g)) andThen f.apply,
        beforeStop compose ((_: SpanInterface[G]).mapK(g)) andThen (_ andThen f.apply)
      )
  }
  object Hooks {
    def apply[F[_]](
      beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder = null,
      justAfterStart: SpanInterface[F] => F[Unit] = null,
      beforeStop: SpanInterface[F] => Option[Throwable] => F[Unit] = null
    )(implicit A: Applicative[F]): Hooks[F] =
      new Hooks(
        Option(beforeStart).getOrElse(locally),
        Option(justAfterStart).getOrElse(_ => A.unit),
        Option(beforeStop).getOrElse(_ => _ => A.unit)
      )
  }
}