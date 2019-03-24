package com.github.fehu.opentracing

import scala.concurrent.Await
import scala.concurrent.duration._

import cats.Eval
import cats.data.EitherT
import cats.effect.IO
import org.scalatest.FreeSpec

class ActivatingSpec extends FreeSpec with Spec {

  def activeSpan() = mockTracer.activeSpan()

  def setFooBarTag() = activeSpan().setTag("foo", "bar")
  val fooBarMap = Map("foo" -> "bar")

  "`activate` syntax should allow to" - {
    "activate and close spans" in {
      assume(activeSpan() eq null)
      val span = mockTracer.buildSpan("test").start()
      activeSpan() shouldBe null
      activate(span) { setFooBarTag() }
      activeSpan() shouldBe null
      span.finish()
      val Seq(finished) = finishedSpans()
      finished.context().spanId() shouldBe span.context().spanId()
      finished.tags().asScala shouldBe fooBarMap
    }
  }

  "`activating` syntax should allow to activate and close spans for" - {
    "any instance of Defer and MonadError" - {
      "EitherT[Eval, Throwable, ?] (using `catsEvalEitherTMonadError`)" in {
        assume(activeSpan() eq null)

        import Activating.catsEvalEitherTMonadError // required for catching thrown errors correctly
        type ETE[A] = EitherT[Eval, Throwable, A]
        // otherwise `activating` has problem deriving `F[_]` from `EitherT[Eval, Throwable, Unit]`
        val evalT0: ETE[Unit] = EitherT.liftF(Eval.later { setFooBarTag(); sys.error("???") })
        val span = mockTracer.buildSpan("test").start()
        val evalT = evalT0.activating(span) // ETE[Unit]
        val eval = evalT.value // Eval[Unit]
        activeSpan() shouldBe null
        eval.value shouldBe 'left
        eval.value.left.get shouldBe a[RuntimeException]
        activeSpan() shouldBe null
        span.finish()
        val Seq(finished) = finishedSpans()
        finished.context().spanId() shouldBe span.context().spanId()
        finished.tags().asScala shouldBe fooBarMap
      }

      "IO async" in {
        assume(activeSpan() eq null)
        val span = mockTracer.buildSpan("test").start()
        val io = IO { setFooBarTag() }.activating(span)
        activeSpan() shouldBe null
        io.unsafeRunSync()
        activeSpan() shouldBe null
        span.finish()
        val Seq(finished) = finishedSpans()
        finished.context().spanId() shouldBe span.context().spanId()
        finished.tags().asScala shouldBe fooBarMap
      }

      "IO to Future" in {
        assume(activeSpan() eq null)
        val span = mockTracer.buildSpan("test").start()
        val io = IO { setFooBarTag() }.activating(span)
        activeSpan() shouldBe null
        Await.result(io.unsafeToFuture(), 50.millis)
        activeSpan() shouldBe null
        span.finish()
        val Seq(finished) = finishedSpans()
        finished.context().spanId() shouldBe span.context().spanId()
        finished.tags().asScala shouldBe fooBarMap
      }
    }
  }

}
