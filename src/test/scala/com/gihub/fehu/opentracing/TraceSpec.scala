package com.gihub.fehu.opentracing

import cats.{ Eval, Later }
import Implicits.defaultTracer
import io.opentracing.util.GlobalTracer
import org.scalatest.{ Assertion, FreeSpec }

class TraceSpec extends FreeSpec with Spec {

  def activeSpan() = GlobalTracer.get().activeSpan()

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

}
