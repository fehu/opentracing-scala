package com.github.fehu.opentracing

import scala.language.higherKinds

import cats.{ Defer, MonadError, ~> }
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import com.github.fehu.opentracing.util.cats.defer
import com.github.fehu.opentracing.util.CatsEvalEitherTMonadError
import io.opentracing.{ Span, Tracer }

/** Run `F ~> F` activating given span. */
trait Activating[F[_]] {
  def apply(
    span: Span,
    finishSpanOnClose: Boolean = false,
    onClose: Either[Throwable, Any] => Span => Unit = _ => _ => {}
  ): F ~> F
}

object Activating extends CatsEvalEitherTMonadError {

  implicit def activatingDeferMonad[F[_]](
    implicit
    D: Defer[F],
    M: MonadError[F, Throwable],
    t: Tracer
  ): Activating[F] =
    (span: Span, finishSpanOnClose: Boolean, onClose: Either[Throwable, Any] => Span => Unit) => λ[F ~> F] { fa =>
      for {
        scope <- defer[F] { util.safe(span)(t.scopeManager().activate(_, finishSpanOnClose)) }
        attempt <- fa.attempt
        _ <- M.pure { scope.foreach { s => try util.safe(s.span())(onClose(attempt)) finally util.closeScopeSafe(s) } }
        a <- M.pure(attempt).rethrow
      } yield a
    }
}