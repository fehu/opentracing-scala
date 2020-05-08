package com.github.fehu.opentracing.akka

import scala.concurrent.Future

import cats.arrow.FunctionK
import cats.effect.{ ContextShift, IO, LiftIO }
import cats.~>
import com.github.fehu.opentracing.Tracing

object AskTracingEffect {
  implicit def askTracingIO(implicit setup: Tracing.TracingSetup, cs: ContextShift[IO]): AskTracing[IO] =
    AskTracing.askTracingFuture.map(
      λ[λ[* => () => Future[Any]] ~> λ[* => IO[Any]]](
        f => IO.fromFuture(IO.delay(f()))
      )
    ).asInstanceOf[AskTracing[IO]]

  implicit def askTracingLiftIO[F[_]](implicit setup: Tracing.TracingSetup, cs: ContextShift[IO], lift: LiftIO[F]): AskTracing[F] =
    askTracingIO.map(FunctionK.lift(lift.liftIO))

}
