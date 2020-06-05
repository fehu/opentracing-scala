package com.arkondata.opentracing.akka

import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorSystem, PoisonPill, Props }
import akka.util.Timeout
import com.arkondata.opentracing.trace
import com.arkondata.opentracing.Spec
import com.arkondata.opentracing.Tracing.TracingSetup.Dummy._
import io.opentracing.Tracer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec

class AskTracingSpec extends AnyFreeSpec with Spec with BeforeAndAfterAll {
  import AskTracingSpec._

  implicit val system = ActorSystem("AskTracingSpec")
  implicit val timeout = Timeout(100.millis)
  import system.dispatcher

  lazy val testActor = system.actorOf(Props(new TestDummy))

  def activeSpanOpt = Option(mockTracer.activeSpan())

  override protected def afterAll(): Unit = {
    testActor ! PoisonPill
    system.terminate()
    super.afterAll()
  }



  "`ask` syntax should" - {
    "help sending traced messages" - {
      "with new root span" in {
        activeSpanOpt shouldBe None

        val future = ask(testActor, "Hello!").tracing("Anyone there?")
        activeSpanOpt shouldBe None
        val result = Await.result(future(), 101.millis)

        activeSpanOpt shouldBe None
        result shouldBe 42
        Thread.sleep(10)
        val Seq(finished) = finishedSpans()
        finished.operationName() shouldBe "Anyone there?"
        finished.tags().asScala shouldBe Map("received" -> "Hello!")

        mockTracer.reset()

        val future2 = ask(testActor, "Hello2").tracing("ABC")
        activeSpanOpt shouldBe None
        val result2 = Await.result(future2(), 101.millis)

        activeSpanOpt shouldBe None
        result2 shouldBe 42
        Thread.sleep(10)
        val Seq(finished2) = finishedSpans()
        finished2.operationName() shouldBe "ABC"
        finished2.tags().asScala shouldBe Map("received" -> "Hello2")
      }

      "with new child span" in {
        activeSpanOpt shouldBe None

        val result = trace.now("test ask") {
          val future = ask(testActor, "???").tracing("???")
          Await.result(future(), 101.millis)
        }
        activeSpanOpt shouldBe None
        result shouldBe 42
        val Seq(finishedInner, finishedOuter) = finishedSpans()
        finishedInner.operationName() shouldBe "???"
        finishedInner.tags().asScala shouldBe Map("received" -> "???")
        finishedInner.parentId() shouldBe finishedOuter.context().spanId()
        finishedOuter.operationName() shouldBe "test ask"
      }
    }
  }

  "There is `TracingActor` that activates received spans" in {
    val actor = system.actorOf(Props(new TestActivating))

    activeSpanOpt shouldBe None
    val future = ask(actor, "activate").tracing("activating")
    activeSpanOpt shouldBe None
    val result = Await.result(future(), 101.millis)

    activeSpanOpt shouldBe None
    result shouldBe 50
    Thread.sleep(10)
    val Seq(finishedActorJob, finishedOuter) = finishedSpans()
    finishedActorJob.operationName() shouldBe "TestActivatingActor"
    finishedActorJob.parentId() shouldBe finishedOuter.context().spanId()
    finishedOuter.tags().asScala shouldBe Map("received" -> "activate")
    finishedOuter.operationName() shouldBe "activating"

    actor ! PoisonPill
  }

  "There is `TracingActor` that creates child span on reception of traced message" in {
    val actor = system.actorOf(Props(new TestChildActivating))

    activeSpanOpt shouldBe None
    val future = ask(actor, "why").tracing("asking", "what" -> "why")
    activeSpanOpt shouldBe None
    val result = Await.result(future(), 101.millis)

    activeSpanOpt shouldBe None
    result shouldBe "why"
    Thread.sleep(10)
    val Seq(finishedActorJob, finishedActor, finishedOuter) = finishedSpans()
    finishedActorJob.operationName() shouldBe "wait"
    finishedActorJob.tags().asScala shouldBe Map("how long?" -> 42)
    finishedActorJob.parentId() shouldBe finishedActor.context().spanId()
    finishedActor.operationName() shouldBe "TestChildBuilding"
    finishedActor.tags().asScala shouldBe Map("received" -> "why")
    finishedActor.parentId() shouldBe finishedOuter.context().spanId()
    finishedOuter.operationName() shouldBe "asking"
    finishedOuter.tags().asScala shouldBe Map("what" -> "why")

    actor ! PoisonPill
  }


}

object AskTracingSpec {

  class TestDummy(implicit val tracer: Tracer) extends Actor with TracingActor {
    def receive: Receive = {
      case msg =>
        actorSpan().foreach(_.setTag("received", msg.toString))
        Thread.sleep(50)
        sender() ! 42
    }
  }

  class TestActivating(implicit val tracer: Tracer) extends Actor with TracingActor.Activating {
    def receive: Receive = {
      case msg =>
        activeSpan().foreach(_.setTag("received", msg.toString))
        trace.now("TestActivatingActor") { Thread.sleep(50) }
        sender() ! 50
    }
  }

  class TestChildActivating(implicit val tracer: Tracer) extends Actor with TracingActor.ActivatingChildSpan {

    def buildChildSpan(message: Any): Tracer.SpanBuilder = buildSpan("TestChildBuilding", "received" -> message.toString)
    def receive: Receive = {
      case msg =>
        trace.now("wait", tags = "how long?" -> 42) { Thread.sleep(42) }
        sender() ! msg
    }
  }
}
