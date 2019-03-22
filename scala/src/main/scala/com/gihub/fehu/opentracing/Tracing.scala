package com.gihub.fehu.opentracing

import scala.language.{ higherKinds, implicitConversions }

import cats.arrow.FunctionK
import cats.data.EitherT
import cats.{ Defer, Eval, Later, MonadError, ~> }
import cats.syntax.applicativeError._
import cats.syntax.comonad._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import io.opentracing.{ Scope, Span, Tracer }
import com.gihub.fehu.opentracing.util.cats.defer


trait Tracing[F0[_], F1[_]] {
  parent =>

  import Tracing._

  protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): F0 ~> F1
  protected def noTrace: F0 ~> F1

  class InterfaceImpl[Out](mkOut: (F0 ~> F1) => Out)(implicit val tracer: Tracer) extends Interface[Out] {
    def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): Out =
      Tracing.Interface.impl(parent, activate, operation, tags)(tracer, (b, a) => mkOut(build(b, a)), mkOut(noTrace))
  }

  def transform(implicit tracer: Tracer): Transform = new Transform
  class Transform(implicit tracer: Tracer) extends InterfaceImpl[F0 ~> F1](locally)

  def apply[A](fa: => F0[A])(implicit tracer: Tracer): PartiallyApplied[A] = new PartiallyApplied(fa)
  class PartiallyApplied[A](fa: F0[A])(implicit tracer: Tracer) extends InterfaceImpl[F1[A]](_(fa))


  def map[R[_]](f: F1 ~> R): Tracing[F0, R] =
    new Tracing[F0, R] {
      protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): F0 ~> R =
        f compose parent.build(spanBuilder, activate)
      protected def noTrace: F0 ~> R = f compose parent.noTrace
    }
  def contramap[S[_]](f: S ~> F0): Tracing[S, F1] =
    new Tracing[S, F1] {
      protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): S ~> F1 =
        parent.build(spanBuilder, activate) compose f
      protected def noTrace: S ~> F1 = parent.noTrace compose f
    }
}

object Tracing extends TracingEvalLaterImplicits {

  /** Common interface for building traces. Starts active by default. */
  trait Interface[Out] {
    def apply(operation: String, tags: Tag*): Out                                  = apply(parent = None, activate = true, operation, buildTags(tags))
    def apply(activate: Boolean, operation: String, tags: Tag*): Out               = apply(parent = None, activate, operation, buildTags(tags))
    def apply(parent: Span, operation: String, tags: Tag*): Out                    = apply(Option(parent), activate = true, operation, buildTags(tags))
    def apply(parent: Span, activate: Boolean, operation: String, tags: Tag*): Out = apply(Option(parent), activate, operation, buildTags(tags))
    private def buildTags(tags: Seq[Tag]): Map[String, TagValue] = tags.map(_.toPair).toMap

    def map[R](f: Out => R): Interface[R] = Interface.Mapped(this, f)

    val tracer: Tracer
    def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): Out
  }
  object Interface {
    case class Mapped[T, R](original: Interface[T], f: T => R) extends Interface[R] {
      val tracer: Tracer = original.tracer
      def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): R =
        f(original(parent, activate, operation, tags))
    }

    type Activate = Boolean
    def impl[Out](parent: Option[Span], activate: Activate, operation: String, tags: Map[String, TagValue])
                 (tracer: Tracer, build: (Tracer.SpanBuilder, Activate) => Out, noTrace: => Out): Out =
      if (tracer eq null) noTrace
      else {
        val builder0 = tags.foldLeft(tracer.buildSpan(operation)) {
          case (acc, (key, TagValue.String(str))) => acc.withTag(key, str)
          case (acc, (key, TagValue.Number(num))) => acc.withTag(key, num)
          case (acc, (key, TagValue.Boolean(b)))  => acc.withTag(key, b)
        }
        val builder1 = parent.map(builder0.asChildOf).getOrElse(builder0)
        build(builder1, activate)
      }
  }


  final case class Tag(key: String, value: TagValue) {
    def toPair: (String, TagValue) = key -> value
  }
  object Tag {
    implicit def tagValueToTag(pair: (String, TagValue)): Tag = Tag(pair._1, pair._2)
    implicit def valueToTag[V: TagValue.Lift](pair: (String, V)): Tag = Tag(pair._1, TagValue.Lift(pair._2))
  }

  sealed trait TagValue
  object TagValue {

    sealed trait Lift[A] extends (A => TagValue)
    object Lift {
      def apply[A](a: A)(implicit lift: Lift[A]): TagValue = lift(a)

      implicit def liftString: Lift[java.lang.String] = define(String.apply)
      implicit def liftJavaNumber[N <: java.lang.Number]: Lift[N] = define(Number.apply)
      implicit def liftInt: Lift[Int]       = define(Number.apply _ compose Int.box)
      implicit def liftLong: Lift[Long]     = define(Number.apply _ compose Long.box)
      implicit def liftDouble: Lift[Double] = define(Number.apply _ compose Double.box)
      implicit def liftFloat: Lift[Float]   = define(Number.apply _ compose Float.box)
      implicit def liftBoolean: Lift[scala.Boolean] = define(Boolean.apply)

      private def define[A](f: A => TagValue) = new Lift[A] { def apply(v1: A): TagValue = f(v1) }
    }

    implicit def apply[A: Lift](a: A): TagValue = Lift(a)

    case class String (get: java.lang.String) extends TagValue { type Value = java.lang.String }
    case class Number (get: java.lang.Number) extends TagValue { type Value = java.lang.Number }
    case class Boolean(get: scala.Boolean)    extends TagValue { type Value = scala.Boolean }
  }

  class TracingSetup(
    val beforeStart: Tracer.SpanBuilder => Tracer.SpanBuilder,
    val beforeStop: Span => Either[Throwable, _] => Unit,
    val beforeCritical: Span => Error => Unit
  )
  object TracingSetup {
    object Dummy {
      implicit object DummyTracingSetup extends TracingSetup(locally, void, void)

      @inline private def void[A, B](a: A)(b: B): Unit = {}
    }
  }


  implicit def tracingDeferMonadError[F[_]](implicit setup: TracingSetup, D: Defer[F], M: MonadError[F, Throwable]): Tracing[F, F] =
    new Tracing[F, F] {
      protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): F ~> F = {
        val builder = setup.beforeStart(spanBuilder)
        λ[F ~> F] { fa =>
          for {
            scopeOrSpan <- defer[F]{ if (activate) Left(builder.startActive(true)) else Right(builder.start()) }
            attempt <- try fa.attempt
                       catch { case critical: Error =>
                                closeScopeOrSpan(scopeOrSpan, setup.beforeCritical(_)(critical))
                                throw critical
                             }
            _ <- M.pure { closeScopeOrSpan(scopeOrSpan, setup.beforeStop(_)(attempt)) }
            result <- M.pure(attempt).rethrow
          } yield result
        }
      }
      private def closeScopeOrSpan(scopeOrSpan: Either[Scope, Span], before: Span => Unit): Unit = scopeOrSpan.fold(
        scope => { try before(scope.span()) finally util.closeScopeSafe(scope) },
        span  => { try before(span)         finally util.finishSpanSafe(span) }
      )

      protected def noTrace: F ~> F = FunctionK.id[F]
    }
}


trait TracingEvalLaterImplicits {
  implicit def tracingEvalLater(implicit setup: Tracing.TracingSetup): Tracing[Later, Eval] =
    Tracing.tracingDeferMonadError[EitherT[Eval, Throwable, ?]]
      .contramap[Later](EitherT.liftK[Eval, Throwable] compose λ[Later ~> Eval](l => l))
      .map(λ[EitherT[Eval, Throwable, ?] ~> Eval](_.valueOr(throw _)))

  private lazy val originalcatsEvalTMonadError = EitherT.catsDataMonadErrorForEitherT[Eval, Throwable]
  implicit lazy val catsEvalEitherTMonadError: MonadError[EitherT[Eval, Throwable, ?], Throwable] =
    new MonadError[EitherT[Eval, Throwable, ?], Throwable] {
      def pure[A](x: A): EitherT[Eval, Throwable, A] =
        originalcatsEvalTMonadError.pure(x)
      def flatMap[A, B](fa: EitherT[Eval, Throwable, A])(f: A => EitherT[Eval, Throwable, B]): EitherT[Eval, Throwable, B] =
        originalcatsEvalTMonadError.flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => EitherT[Eval, Throwable, Either[A, B]]): EitherT[Eval, Throwable, B] =
        originalcatsEvalTMonadError.tailRecM(a)(f)
      def raiseError[A](e: Throwable): EitherT[Eval, Throwable, A] =
        originalcatsEvalTMonadError.raiseError(e)
      def handleErrorWith[A](fa: EitherT[Eval, Throwable, A])(f: Throwable => EitherT[Eval, Throwable, A]): EitherT[Eval, Throwable, A] =
        originalcatsEvalTMonadError.handleErrorWith {
          EitherT(defer[Eval]{
            Either
              .catchNonFatal { fa.value.extract }
              .valueOr(Left(_))
          })
        }(f)
    }
}