package com.github.fehu.opentracing.akka

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorSystem, PoisonPill, Props }
import akka.util.Timeout
import com.gihub.fehu.opentracing.trace
import com.gihub.fehu.opentracing.NullableImplicits.Tracer._
import com.gihub.fehu.opentracing.Implicits._
import com.gihub.fehu.opentracing.Spec
import io.opentracing.contrib.akka.TracedAbstractActor
import org.scalatest.{ BeforeAndAfterAll, FreeSpec }

class AskTracingSpec extends FreeSpec with Spec with BeforeAndAfterAll {
  import AskTracingSpec._

  implicit val system = ActorSystem("AskTracingSpec")
  implicit val timeout = Timeout(100.millis)
  import system.dispatcher

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }



  "`ask` syntax should" - {
    "help sending traced messages" - {
      "with new root span" in {
        val actor = system.actorOf(Props[TestDummy])
        activeSpanOpt shouldBe None

        val future = ask(actor, "Hello!").tracing("Anyone there?")
        activeSpanOpt shouldBe None
        val result = Await.result(future, 101.millis)

        activeSpanOpt shouldBe None
        result shouldBe 42
        val Seq(finished) = finishedSpans()
        finished.operationName() shouldBe "Anyone there?"
        finished.tags().asScala shouldBe Map("received" -> "Hello!")

        val future2 = ask(actor, "Hello2").tracing("ABC?")
        activeSpanOpt shouldBe None
        val result2 = Await.result(future2, 101.millis)

        activeSpanOpt shouldBe None
        result2 shouldBe 42
        val Seq(finished2) = finishedSpans()
        finished2.operationName() shouldBe "ABC"
        finished2.tags().asScala shouldBe Map("received" -> "Hello2")

        actor ! PoisonPill
      }

      "with new child span" in {
        val actor = system.actorOf(Props[TestDummy])
        activeSpanOpt shouldBe None

        val result = trace.now("test ask") {
          val future = ask(actor, "???").tracing("???")
          Await.result(future, 101.millis)
        }
        activeSpanOpt shouldBe None
        result shouldBe 42
        val Seq(finishedInner, finishedOuter) = finishedSpans()
        finishedInner.operationName() shouldBe "???"
        finishedInner.tags().asScala shouldBe Map("received" -> "???")
        finishedInner.parentId() shouldBe finishedOuter.context().spanId()
        finishedOuter.operationName() shouldBe "test ask"

        actor ! PoisonPill
      }
    }
  }


}

object AskTracingSpec {
  class TestDummy extends Actor with TracedAbstractActor {
    def receive: Receive = {
      case msg =>
        println(s"receive: $msg")
        tracer().activeSpan().setTag("received", msg.toString)
        tracer().activeSpan()
        Thread.sleep(50)
        sender() ! 42
    }
  }
}
