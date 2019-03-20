package com.gihub.fehu.opentracing

import cats.{ Eval, Later }
import NullableImplicits.Tracer.defaultNullableTracer
import cats.effect.IO
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.scalatest.{ Assertion, FreeSpec }

class TraceSpec extends FreeSpec with Spec {

  def activeSpan() = Option(GlobalTracer.get()).map(_.activeSpan()).orNull

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
    implicit val defaultNullableTracer: Tracer = null
    trace.now("undefined") { activeSpan() shouldBe null }
    finishedSpans() shouldBe empty
  }

  "support tracing types of classes `Defer` a `MonadError`" in {
    val io = IO { activeSpan() should not be null }.tracing("IO")
    activeSpan() shouldBe null
    finishedSpans() shouldBe empty
    io.unsafeRunSync()
    val Seq(finished) = finishedSpans()
    finished.operationName() shouldBe "IO"
  }

}
