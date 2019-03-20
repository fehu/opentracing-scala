package com.gihub.fehu.opentracing

import scala.language.{ higherKinds, implicitConversions }
import scala.util.Try

import cats.{ Eval, Id, Later, ~> }
import io.opentracing.{ Scope, Span, Tracer }


trait Tracing[F0[_], F1[_]] {
  import Tracing._

  protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): F0 ~> F1

  class InterfaceImpl[Out](mkOut: (F0 ~> F1) => Out)(implicit val tracer: Tracer) extends Interface[Out] {
    def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): Out = {
      val builder0 = tags.foldLeft(tracer.buildSpan(operation)) {
        case (acc, (key, TagValue.String(str))) => acc.withTag(key, str)
        case (acc, (key, TagValue.Number(num))) => acc.withTag(key, num)
        case (acc, (key, TagValue.Boolean(b)))  => acc.withTag(key, b)
      }
      val builder1 = parent.map(builder0.asChildOf).getOrElse(builder0)
      mkOut(build(builder1, activate))
    }
  }

  def transform(implicit tracer: Tracer): Transform = new Transform
  class Transform(implicit tracer: Tracer) extends InterfaceImpl[F0 ~> F1](locally) with Transformation[F0, F1]

  def apply[A](fa: => F0[A])(implicit tracer: Tracer): PartiallyApplied[A] = new PartiallyApplied(fa)
  class PartiallyApplied[A](fa: F0[A])(implicit tracer: Tracer) extends InterfaceImpl[F1[A]](_(fa))

}

object Tracing {

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
  }

  trait Transformation[A[_], B[_]] extends Interface[A ~> B] {
    def map    [C[_]](f: B ~> C): Transformation[A, C] = Transformation.AndThen(this, f)
    def andThen[C[_]](f: B ~> C): Transformation[A, C] = Transformation.AndThen(this, f)

    def contramap[C[_]](f: C ~> A): Transformation[C, B] = Transformation.Compose(this, f)
    def compose  [C[_]](f: C ~> A): Transformation[C, B] = Transformation.Compose(this, f)
  }
  object Transformation {
    case class Compose[A[_], B[_], C[_]](f: Transformation[B, C], g: A ~> B) extends Transformation[A, C] {
      val tracer: Tracer = f.tracer
      def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): A ~> C =
        f(parent, activate, operation, tags) compose g
    }
    case class AndThen[A[_], B[_], C[_]](f: Transformation[A, B], g: B ~> C) extends Transformation[A, C] {
      val tracer: Tracer = f.tracer
      def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): A ~> C =
        g compose f(parent, activate, operation, tags)
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
    val beforeStopScope: Try[_] => Scope => Scope,
    val beforeStopSpan: Try[_] => Span => Span
  )
  object TracingSetup {
    implicit object DummyTracingSetup extends TracingSetup(locally, _ => locally, _ => locally)
  }


  implicit def tracingEvalLater(implicit setup: TracingSetup): TracingEvalLater = new TracingEvalLater(setup)
  class TracingEvalLater(val setup: TracingSetup) extends Tracing[Later, Eval] {
    protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): Later ~> Eval = {
      val builder = setup.beforeStart(spanBuilder)
      λ[Later ~> Eval] { later =>
        for {
          scopeOrSpan <- Eval.later {
            if (activate) Left(builder.startActive(true)) else Right(builder.start())
          }
          tried <- Eval.now{ Try(later.value) }
          _ <- Eval.now {
            scopeOrSpan.fold(
              setup.beforeStopScope(tried) andThen (_.close()),
              setup.beforeStopSpan (tried) andThen (_.finish())
            )
          }
        } yield tried.get
      }
    }
  }

}

