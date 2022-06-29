package io.github.fehu.opentracing

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import cats.effect.{ Async, Sync, Temporal }
import cats.effect.std.Dispatcher
import cats.effect.syntax.async.*
import cats.effect.syntax.concurrent.*
import cats.syntax.apply.*
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.scalatest.Ignore
import org.scalatest.freespec.AnyFreeSpec

import io.github.fehu.opentracing.syntax.*

@Ignore
abstract class TraceSpec[F[_]: Async: Traced] extends AnyFreeSpec with Spec {
  val dispatcher: Dispatcher[F]

  implicit lazy val tracedSetup: Traced.Setup = Traced.Setup.default(mockTracer)
  implicit lazy val tracedSpan: Traced.ActiveSpan = Traced.ActiveSpan.empty

  "Trace nested defer / delay" in dispatcher.unsafeRunSync{
    Sync[F].defer {
      Sync[F].defer {
        Sync[F].defer {
          Sync[F].delay {
            ()
          }.trace("last", "depth" -> 3)
        }.trace("inner", "depth" -> 2)
      }.trace("middle", "depth" -> 1)
    }.trace("outer", "depth" -> 0)
     .map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "last",   spanId = 4, parentId = 3, tags = Map("depth" -> Int.box(3))),
        TestedSpan(operationName = "inner",  spanId = 3, parentId = 2, tags = Map("depth" -> Int.box(2))),
        TestedSpan(operationName = "middle", spanId = 2, parentId = 1, tags = Map("depth" -> Int.box(1))),
        TestedSpan(operationName = "outer",  spanId = 1, parentId = 0, tags = Map("depth" -> Int.box(0)))
      )
    }
  }

  "Trace nested map / flatMap" in dispatcher.unsafeRunSync{
    for {
      _ <- Sync[F].pure(()).trace("one")
      _ <- Sync[F].pure(()).trace("two")
      _ <- Sync[F].pure(()).trace("three")
      _ <- Sync[F].pure(()).trace("four")
      _ <- Sync[F].pure(()).trace("five")
    } yield {
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "one",   spanId = 1, parentId = 0),
        TestedSpan(operationName = "two",   spanId = 2, parentId = 1),
        TestedSpan(operationName = "three", spanId = 3, parentId = 2),
        TestedSpan(operationName = "four",  spanId = 4, parentId = 3),
        TestedSpan(operationName = "five",  spanId = 5, parentId = 4)
      )
    }
  }

  "Trace when timer is used" in dispatcher.unsafeRunSync{
    val f1 = Temporal[F].sleep(500.millis).trace("s1") *> Sync[F].delay(()).trace("f1")
    val f2 = Sync[F].defer(Temporal[F].sleep(500.millis).trace("s2")).trace("f2")
    val f = (f1 *> f2).trace("f")
    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "s1", spanId = 2, parentId = 1),
        TestedSpan(operationName = "f1", spanId = 3, parentId = 2),
        TestedSpan(operationName = "s2", spanId = 5, parentId = 4),
        TestedSpan(operationName = "f2", spanId = 4, parentId = 3),
        TestedSpan(operationName = "f",  spanId = 1, parentId = 0)
      )
    }
  }

  "Trace when evaluated on other context" in dispatcher.unsafeRunSync {
    val f1 = Sync[F].delay(()).trace("f1")
    val f2 = Sync[F].delay(()).trace("f2")
    val f3 = Sync[F].delay(()).trace("f3")
    val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val f = (f1 *> f2.evalOn(ec) *> f3).trace("f")
    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "f1", spanId = 2, parentId = 1),
        TestedSpan(operationName = "f2", spanId = 3, parentId = 2),
        TestedSpan(operationName = "f3", spanId = 4, parentId = 2),
        TestedSpan(operationName = "f",  spanId = 1, parentId = 0)
      )
    }
  }

  "Trace when starting and joining fiber" in dispatcher.unsafeRunSync {
    val f1 = Sync[F].delay(()).trace("f1")
    val f2 = Temporal[F].sleep(40.millis).trace("f2")
    val f3 = Sync[F].delay(()).trace("f3")
    val f4 = Sync[F].delay(()).trace("f4")
    val f5 = Sync[F].delay(()).trace("f5")
    val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val f = (f1 *> f2.startOn(ec) <* f3)
              .trace("fff")
              .flatMap(_.join *> f4)
              .trace("ff")
              .productR(f5)
              .trace("f")
    f.map { _ =>
      finishedSpans() should (
        equal(Seq(
          TestedSpan(operationName = "f1",  spanId = 4, parentId = 3),
          TestedSpan(operationName = "f3",  spanId = 5, parentId = 4), // <- the difference
          TestedSpan(operationName = "fff", spanId = 3, parentId = 2),
          TestedSpan(operationName = "f2",  spanId = 6, parentId = 4), // <- the difference
          TestedSpan(operationName = "f4",  spanId = 7, parentId = 4),
          TestedSpan(operationName = "ff",  spanId = 2, parentId = 1),
          TestedSpan(operationName = "f5",  spanId = 8, parentId = 7),
          TestedSpan(operationName = "f",   spanId = 1, parentId = 0),
        )) or
        equal(Seq(
          TestedSpan(operationName = "f1",  spanId = 4, parentId = 3),
          TestedSpan(operationName = "f3",  spanId = 6, parentId = 4), // <- the difference
          TestedSpan(operationName = "fff", spanId = 3, parentId = 2),
          TestedSpan(operationName = "f2",  spanId = 5, parentId = 4), // <- the difference
          TestedSpan(operationName = "f4",  spanId = 7, parentId = 4),
          TestedSpan(operationName = "ff",  spanId = 2, parentId = 1),
          TestedSpan(operationName = "f5",  spanId = 8, parentId = 7),
          TestedSpan(operationName = "f",   spanId = 1, parentId = 0),
        ))
      )}
  }

  "Trace running in background" in dispatcher.unsafeRunSync {
    val f1 = Sync[F].delay(()).trace("f1")
    val f2 = Sync[F].delay(()).trace("f2")
    val f3 = Sync[F].delay(()).trace("f3")
    val f4 = Sync[F].delay(()).trace("f4")
    val f5 = Sync[F].delay(()).trace("f5")
    val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val f = (f1 *> (f2 *> f3).backgroundOn(ec).use(_ <* f4).trace("ff") <* f5).trace("f")
    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "f1", spanId = 2, parentId = 1),
        TestedSpan(operationName = "f2", spanId = 4, parentId = 3),
        TestedSpan(operationName = "f3", spanId = 5, parentId = 4),
        TestedSpan(operationName = "f4", spanId = 6, parentId = 3),
        TestedSpan(operationName = "ff", spanId = 3, parentId = 2),
        TestedSpan(operationName = "f5", spanId = 7, parentId = 6),
        TestedSpan(operationName = "f",  spanId = 1, parentId = 0),
      )
    }
  }

  "Trace memorize" in dispatcher.unsafeRunSync {
    val f1 = Sync[F].delay(()).trace("f1")
    val f2 = Sync[F].delay(()).trace("f2")
    val f3 = Sync[F].delay(()).trace("f3")
    val f4 = Sync[F].delay(()).trace("f4")
    val f5 = Sync[F].delay(()).trace("f5")
    val f = (f1 *> f2 *> f3.memoize.flatMap(ff => ff *> f4 *> ff) *> f5).trace("f")
    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "f1", spanId = 2, parentId = 1),
        TestedSpan(operationName = "f2", spanId = 3, parentId = 2),
        TestedSpan(operationName = "f3", spanId = 4, parentId = 3),
        TestedSpan(operationName = "f4", spanId = 5, parentId = 4),
        TestedSpan(operationName = "f5", spanId = 6, parentId = 5),
        TestedSpan(operationName = "f",  spanId = 1, parentId = 0),
      )
    }
  }

  "Trace timeouts" in dispatcher.unsafeRunSync {
    val f1 = Sync[F].delay(()).trace("f1")
    val f2 = Temporal[F].sleep(50.millis).trace("f2-1") *> Temporal[F].sleep(50.millis).trace("f2-2")
    val f3 = Sync[F].delay(()).trace("f3")
    val f4 = Sync[F].delay(()).trace("f4")
    val f5 = Temporal[F].sleep(50.millis).trace("f5-1") *> Temporal[F].sleep(50.millis).trace("f5-2")
    val f6 = Sync[F].delay(()).trace("f6")
    val f7 = Temporal[F].sleep(10.millis).trace("f7-1") *> Temporal[F].sleep(10.millis).trace("f7-2")
    val f8 = Sync[F].delay(()).trace("f8")
    val f = (f1 *>
             Temporal[F].timeoutTo(f2, 40.millis, f3).trace("t1") *>
             f4 *>
             Temporal[F].timeout(f5, 40.millis).trace("t2").attempt *>
             f6 *>
             Temporal[F].timeout(f7, 40.millis).trace("t3").attempt *>
             f8
            ).trace("f")

    f.map { _ =>
      finishedSpans() shouldBe Seq(
        TestedSpan(operationName = "f1",   spanId = 2,  parentId = 1),
        TestedSpan(operationName = "f2-1", spanId = 4,  parentId = 3),
        TestedSpan(operationName = "f3",   spanId = 5,  parentId = 3),
        TestedSpan(operationName = "t1",   spanId = 3,  parentId = 2),
        TestedSpan(operationName = "f4",   spanId = 6,  parentId = 5),
        TestedSpan(operationName = "f5-1", spanId = 8,  parentId = 7),
        TestedSpan(operationName = "t2",   spanId = 7,  parentId = 6),
        TestedSpan(operationName = "f6",   spanId = 9,  parentId = 6),
        TestedSpan(operationName = "f7-1", spanId = 11, parentId = 10),
        TestedSpan(operationName = "f7-2", spanId = 12, parentId = 11),
        TestedSpan(operationName = "t3",   spanId = 10, parentId = 9),
        TestedSpan(operationName = "f8",   spanId = 13, parentId = 10),
        TestedSpan(operationName = "f",    spanId = 1,  parentId = 0),
      )
    }
  }

}
