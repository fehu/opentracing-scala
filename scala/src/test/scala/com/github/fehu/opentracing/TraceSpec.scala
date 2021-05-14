package com.github.fehu.opentracing

import scala.concurrent.duration._

import org.scalatest.freespec.AnyFreeSpec
import cats.effect.{ ContextShift, Effect, Timer }
import cats.effect.syntax.effect._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.scalatest.Ignore

import com.github.fehu.opentracing.syntax._
import com.github.fehu.opentracing.util.ErrorLogger

@Ignore
abstract class TraceSpec[F[_]: Traced] extends AnyFreeSpec with Spec {
  implicit val effect: Effect[F]
  implicit val cs: ContextShift[F]
  implicit val timer: Timer[F]

  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty, ErrorLogger.stdout)

  "Trace nested defer / delay" in {
    Effect[F].defer {
      Effect[F].defer {
        Effect[F].defer {
          Effect[F].delay {
            ()
          }.trace("last", "depth" -> 3)
        }.trace("inner", "depth" -> 2)
      }.trace("middle", "depth" -> 1)
    }.trace("outer", "depth" -> 0)
     .map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(traceId = 1, spanId = 5, parentId = 4, operationName = "last",   tags = Map("depth" -> Int.box(3))),
        TestedSpan(traceId = 1, spanId = 4, parentId = 3, operationName = "inner",  tags = Map("depth" -> Int.box(2))),
        TestedSpan(traceId = 1, spanId = 3, parentId = 2, operationName = "middle", tags = Map("depth" -> Int.box(1))),
        TestedSpan(traceId = 1, spanId = 2, parentId = 0, operationName = "outer",  tags = Map("depth" -> Int.box(0)))
      )
    }.toIO.unsafeRunSync()
  }

  "Trace nested map / flatMap" in {
    for {
      _ <- Effect[F].pure(()).trace("one")
      _ <- Effect[F].pure(()).trace("two")
      _ <- Effect[F].pure(()).trace("three")
      _ <- Effect[F].pure(()).trace("four")
      _ <- Effect[F].pure(()).trace("five")
    } yield {
      finishedSpans() shouldBe Seq(
        TestedSpan(traceId = 6, spanId = 7,  parentId = 0,  operationName = "one"),
        TestedSpan(traceId = 6, spanId = 8,  parentId = 7,  operationName = "two"),
        TestedSpan(traceId = 6, spanId = 9,  parentId = 8,  operationName = "three"),
        TestedSpan(traceId = 6, spanId = 10, parentId = 9,  operationName = "four"),
        TestedSpan(traceId = 6, spanId = 11, parentId = 10, operationName = "five")
      )
    }
  }.toIO.unsafeRunSync()

  "Trace when context is shifted and timer is used" in {
    val f1 = Timer[F].sleep(500.millis) *> Effect[F].delay(()).trace("f1")
    val f2 = (Timer[F].sleep(500.millis) *> Effect[F].delay(())).trace("f2")
    val f = (f1 *> ContextShift[F].shift *> f2).trace("f")
    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(traceId = 12, spanId = 14, parentId = 13, operationName = "f1"),
        TestedSpan(traceId = 12, spanId = 15, parentId = 14, operationName = "f2"),
        TestedSpan(traceId = 12, spanId = 13, parentId = 0,  operationName = "f")
      )
    }.toIO.unsafeRunSync()
  }
}
