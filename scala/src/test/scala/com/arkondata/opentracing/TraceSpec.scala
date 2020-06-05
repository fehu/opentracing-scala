package com.arkondata.opentracing

import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

import cats.{ Eval, Later }
import cats.effect.IO
import com.arkondata.opentracing.Tracing.TracingSetup
import io.opentracing.Tracer
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec

class TraceSpec extends AnyFreeSpec with Spec {

  implicit val tracingSetup: TracingSetup = TracingSetup.Dummy.DummyTracingSetup

  def activeSpan()(implicit tracer: Tracer) = Option(tracer).map(_.activeSpan()).orNull

  "`trace` syntax should allow to" - {
    "build active spans" - {
      "eagerly" in {
        val now: Assertion = trace.now("foo"){
          activeSpan() should not be null
        }
        activeSpan() shouldBe null
        val Seq(finished) = finishedSpans()
        finished.operationName() shouldBe "foo"
      }

      "lazily" in {
        lazy val error = new Exception("error")
        val later: Eval[_] = trace.later("bar") { throw error }
        activeSpan() shouldBe null
        finishedSpans() shouldBe empty
        the[Exception] thrownBy later.value shouldBe error
        val Seq(finished) = finishedSpans()
        finished.operationName() shouldBe "bar"
      }
    }

    "specify tags at span creation" in {
      trace.now("baz",
        "id" -> 1,
        "debug" -> true
      ){
        activeSpan() should not be null
      }
      activeSpan() shouldBe null
      val Seq(finished) = finishedSpans()
      finished.operationName() shouldBe "baz"
      finished.tags().asScala shouldBe Map(
        "id" -> Int.box(1),
        "debug" -> Boolean.box(true)
      )
    }
  }

  "`tracing` syntax should allow to do the same" in {
    val later = Later { activeSpan() should not be null }.tracing("123")
    activeSpan() shouldBe null
    finishedSpans() shouldBe empty
    later.value
    val Seq(finished) = finishedSpans()
    finished.operationName() shouldBe "123"
  }

  "lack of defined tracer should not affect other functionality" in {
    implicit val mockTracer: Tracer = null
    trace.now("undefined") { activeSpan() shouldBe null }
    finishedSpans() shouldBe empty
  }

  "support tracing types of classes `Defer` a `MonadError` (IO sync)" in {
    val io = IO { activeSpan() should not be null }.tracing("IO")
    activeSpan() shouldBe null
    finishedSpans() shouldBe empty
    io.unsafeRunSync()
    val Seq(finished) = finishedSpans()
    finished.operationName() shouldBe "IO"
  }

  "support tracing types of classes `Defer` a `MonadError` (IO async)" in {
    val io = IO { activeSpan() should not be null }.tracing("IO")
    activeSpan() shouldBe null
    finishedSpans() shouldBe empty
    trace.now("run IO") {
      io.unsafeRunAsyncAndForget()
    }
    Thread.sleep(50)
    val Seq(finishedInner, finishedOuter) = finishedSpans()
    finishedInner.operationName() shouldBe "IO"
    finishedOuter.operationName() shouldBe "run IO"
    finishedInner.parentId() shouldBe finishedOuter.context().spanId()
  }

  "support tracing types of classes `Defer` a `MonadError` (IO to future)" in {
    val io = IO { activeSpan() should not be null }.tracing("IO")
    activeSpan() shouldBe null
    finishedSpans() shouldBe empty

    trace.now("Await") {
      val future = trace.now("run IO") { io.unsafeToFuture() }
      Await.result(future, 10.millis)
    }

    val Seq(finishedInner, finishedMiddle, finishedOuter) = finishedSpans()
    finishedInner.operationName() shouldBe "IO"
    finishedInner.parentId() shouldBe finishedMiddle.context().spanId()
    finishedMiddle.operationName() shouldBe "run IO"
    finishedMiddle.parentId() shouldBe finishedOuter.context().spanId()
    finishedOuter.operationName() shouldBe "Await"
  }

  "allow to customize spans preparation and termination" in {
    // use same name to override implicit
    implicit val tracingSetup: TracingSetup = new TracingSetup(
      beforeStart = _.withTag("beforeStart", true),
      justAfterStart = _.log("justAfterStart"),
      beforeStop = res => _
        .log("beforeStop")
        .setTag("result", res.toString)
        .setTag("success", res.isRight)
    )

    trace.now("123") { activeSpan().log("123"); 123 }

    val Seq(finished) = finishedSpans()
    finished.operationName() shouldBe "123"
    finished.tags().asScala shouldBe Map(
      "beforeStart" -> true,
      "result"  -> Right(123).toString,
      "success" -> true
    )
    finished.logEntries().asScala.flatMap(_.fields().values().asScala) shouldBe
      Seq("justAfterStart", "123", "beforeStop")
  }

}
