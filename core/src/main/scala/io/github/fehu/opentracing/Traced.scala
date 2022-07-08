package io.github.fehu.opentracing

import scala.language.implicitConversions

import cats.{ Show, ~> }
import cats.effect.Resource
import cats.syntax.show.*
import io.opentracing.{ Span, SpanContext, Tracer, tag }

import io.github.fehu.opentracing.internal.compat.*
import io.github.fehu.opentracing.propagation.Propagation
import io.github.fehu.opentracing.util.ErrorLogger
import io.github.fehu.opentracing.util.FunctionK2.~~>

trait Traced2[T[_[_], _], F[_]] extends Traced[T[F, _]] {
  def currentRunParams: T[F, Traced.RunParams]
  def run[A](traced: T[F, A], params: Traced.RunParams): F[A]

  def lift[A](ua: F[A]): T[F, A]
  def mapK[G[_]](f: F ~> G): T[F, _] ~> T[G, _]
}

trait Traced[F[_]] extends Traced.Interface[F] {
  def pure[A](a: A): F[A]
  def defer[A](fa: => F[A]): F[A]

  def currentSpan: Traced.SpanInterface[F]

  def forceCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]
  /** Sets `active` span if no other is set. */
  def recoverCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]

  def injectContext(context: SpanContext): Traced.Interface[F]
  def injectContextFrom(carrier: Propagation#Carrier): Traced.Interface[F]

  def extractContext[C <: Propagation#Carrier](carrier: C): F[Option[C]]
}

object Traced {
  def apply[F[_]](implicit traced: Traced[F]): Traced[F] = traced

  trait Interface[F[_]] {
    def apply[A](op: String, tags: Traced.Tag*)(fa: F[A]): F[A]
    def spanResource(op: String, tags: Traced.Tag*): Resource[F, ActiveSpan]

    final def apply[A](op: Operation)(fa: F[A]): F[A] = apply(op.operation, op.tags*)(fa)
    final def spanResource[A](op: Operation): Resource[F, ActiveSpan] = spanResource(op.operation, op.tags*)

    final def apply[A](builder: Operation.Builder)(fa: F[A]): F[A] = apply(builder(Operation))(fa)
    final def spanResource[A](builder: Operation.Builder): Resource[F, ActiveSpan] = spanResource(builder(Operation))

    def withParent(span: ActiveSpan): Interface[F]
    def withParent(span: SpanContext): Interface[F]
    def withoutParent: Interface[F]
  }

  final case class Operation(operation: String, tags: Seq[Traced.Tag])
  object Operation {
    def span(op: String, tags: Traced.Tag*): Operation = new Operation(op, tags)

    type Builder = Operation.type => Operation
  }

  class Tag(val apply: Taggable.PartiallyApplied) extends AnyVal

  object Tag {
    implicit def stringPair[A](p: (String, A))(implicit t: Taggable[A]): Tag = new Tag(t(p._1, p._2))
    implicit def tagPair[A](p: (tag.Tag[A], A))(implicit t: Taggable[A]): Tag = new Tag(t(p._1.getKey.nn, p._2))
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
        def apply(builder: Tracer.SpanBuilder, key: String, value: String): Tracer.SpanBuilder = builder.withTag(key, value).nn
        def apply(builder: Span, key: String, value: String): Span = builder.setTag(key, value).nn
      }
    implicit lazy val boolIsTaggable: Taggable[Boolean] =
      new Taggable[Boolean] {
        def apply(builder: Tracer.SpanBuilder, key: String, value: Boolean): Tracer.SpanBuilder = builder.withTag(key, value).nn
        def apply(builder: Span, key: String, value: Boolean): Span = builder.setTag(key, value).nn
      }
    implicit lazy val numberIsTaggable: Taggable[Number] = new Taggable[Number] {
      def apply(builder: Tracer.SpanBuilder, key: String, value: Number): Tracer.SpanBuilder = builder.withTag(key, value).nn
      def apply(builder: Span, key: String, value: Number): Span = builder.setTag(key, value).nn
    }
    implicit lazy val intIsTaggable: Taggable[Int]       = numberIsTaggable.contramap(Int.box)
    implicit lazy val longIsTaggable: Taggable[Long]     = numberIsTaggable.contramap(Long.box)
    implicit lazy val doubleIsTaggable: Taggable[Double] = numberIsTaggable.contramap(Double.box)
    implicit lazy val floatIsTaggable: Taggable[Float]   = numberIsTaggable.contramap(Float.box)

    implicit def shownIsTaggable[A: Show]: Taggable[A] = stringIsTaggable.contramap(_.show)
  }

  trait SpanInterface[F[_]] {
    def context: F[Option[SpanContext]]

    def setOperation(op: String): F[Unit]

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
    def setTags(tags: Traced.Tag*): AccumulativeSpanInterface[F] = accumulate(_.setTags(tags*))

    def log(fields: (String, Any)*): AccumulativeSpanInterface[F] = accumulate(_.log(fields*))
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
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.setTags(tags*)
      }

    def log(fields: (String, Any)*): AccumulativeSpanInterface ~~> AccumulativeSpanInterface =
      new (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): AccumulativeSpanInterface[A] = fa.log(fields*)
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

  final case class Setup(tracer: Tracer, hooks: Hooks, logError: ErrorLogger)
  object Setup {
    def default(tracer: Tracer): Setup = Setup(tracer, Hooks(), ErrorLogger.stdout)
  }

  final case class RunParams(setup: Setup, activeSpan: ActiveSpan)

  object RunParams {
    def apply(tracer: Tracer, hooks: Hooks, logError: ErrorLogger, activeSpan: ActiveSpan): RunParams =
      RunParams(Setup(tracer, hooks, logError), activeSpan)

    implicit def fromPair(p: (Setup, ActiveSpan)): RunParams = RunParams(p._1, p._2)

    implicit def fromScope(implicit active: ActiveSpan, setup: Setup): RunParams = RunParams(setup, active)
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
    val justAfterStart: SpanInterface ~~> JustAfterStartRhs,
    val beforeStop: SpanInterface ~~> BeforeStopRhs
  )

  type JustAfterStartRhs[F[_]] = List[SpanInterface[F] => F[Unit]]
  type BeforeStopRhs[F[_]] = Option[Throwable] => List[SpanInterface[F] => F[Unit]]

  object Hooks {
    def apply(
      beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder = locally,
      justAfterStart: SpanInterfaceK2 => (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) = _.noop,
      beforeStop: SpanInterfaceK2 => Option[Throwable] => (AccumulativeSpanInterface ~~> AccumulativeSpanInterface) = s => _ => s.noop
    ): Hooks =
      new Hooks(
        beforeStart,
        accumulateK2 compose justAfterStart(SpanInterfaceK2) compose accumulativeSpanInterfaceK2,
        new (SpanInterface ~~> BeforeStopRhs) {
          def apply[A[_]](fa: SpanInterface[A]): Option[Throwable] => List[SpanInterface[A] => A[Unit]] =
            e => beforeStop(SpanInterfaceK2)(e)(new AccumulativeSpanInterface(fa, Nil)).accumulated
        }
      )

    lazy val accumulativeSpanInterfaceK2: SpanInterface ~~> AccumulativeSpanInterface =
      new (SpanInterface ~~> AccumulativeSpanInterface) {
        def apply[A[_]](fa: SpanInterface[A]): AccumulativeSpanInterface[A] = new AccumulativeSpanInterface(fa, Nil)
      }

    lazy val accumulateK2: AccumulativeSpanInterface ~~> JustAfterStartRhs =
      new (AccumulativeSpanInterface ~~> JustAfterStartRhs) {
        def apply[A[_]](fa: AccumulativeSpanInterface[A]): List[SpanInterface[A] => A[Unit]] = fa.accumulated
      }
  }
}
