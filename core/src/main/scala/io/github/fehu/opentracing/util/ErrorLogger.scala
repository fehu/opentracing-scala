package io.github.fehu.opentracing.util

import cats.{ Applicative, Defer }

trait ErrorLogger {
  def apply[F[_]: Applicative: Defer](message: String, error: Throwable): F[Unit]
}

object ErrorLogger {
  lazy val stdout: ErrorLogger = new ErrorLogger {
    def apply[F[_]: Applicative: Defer](message: String, error: Throwable): F[Unit] =
      Defer[F].defer(Applicative[F].pure {
        println(s"[ERROR] $message\nCaused by: $error")
      })
  }
}
