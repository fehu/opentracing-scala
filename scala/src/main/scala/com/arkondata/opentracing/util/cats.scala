package com.arkondata.opentracing.util

import scala.language.higherKinds

import _root_.cats.{ Applicative, Defer, Eval, Id, MonadError }
import _root_.cats.data.EitherT
import _root_.cats.syntax.comonad._
import _root_.cats.syntax.either._

object cats extends CatsEvalEitherTMonadError {

  def defer[F[_]]: DeferOps[F] = DeferOps.asInstanceOf[DeferOps[F]]

  final class DeferOps[F[_]] {
    def apply[A](a: => A)(implicit D: Defer[F], A: Applicative[F]): F[A] = D.defer(A.pure(a))
  }
  private lazy val DeferOps = new DeferOps[Id]

}


trait CatsEvalEitherTMonadError {
  private lazy val originalcatsEvalTMonadError = EitherT.catsDataMonadErrorForEitherT[Eval, Throwable]
  implicit lazy val catsEvalEitherTMonadError: MonadError[EitherT[Eval, Throwable, *], Throwable] =
    new MonadError[EitherT[Eval, Throwable, *], Throwable] {
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
          EitherT(cats.defer[Eval]{
            Either
              .catchNonFatal { fa.value.extract }
              .valueOr(Left(_))
          })
        }(f)
    }
}