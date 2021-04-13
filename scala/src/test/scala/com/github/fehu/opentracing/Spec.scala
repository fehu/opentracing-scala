package com.github.fehu.opentracing

import java.util.concurrent.atomic.AtomicLong

import scala.collection.JavaConverters._

import _root_.io.opentracing.mock.{ MockSpan, MockTracer }
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, Suite }
import org.scalatest.matchers.should.Matchers

trait Spec extends Matchers with BeforeAndAfter with BeforeAndAfterAll {
  this: Suite =>

  implicit lazy val mockTracer: MockTracer = new MockTracer

  before {
    mockTracer.reset()
  }

  type TestedSpan = Spec.TestedSpan
  val TestedSpan: Spec.TestedSpan.type = Spec.TestedSpan

  def finishedSpans(): Seq[Spec.TestedSpan] = mockTracer.finishedSpans().asScala.toSeq.map(Spec.shiftedTestedSpan(_offset, _))

  private var _offset: Long = 0
  override protected def beforeAll(): Unit = {
    _offset = Spec.mockSpanNextIdField.get()
  }
}

object Spec {
  case class TestedSpan(
    traceId: Long,
    spanId: Long,
    parentId: Long,
    operationName: String,
    tags: Map[String, AnyRef] = Map(),
    logs: List[Map[String, Any]] = Nil
  )

  private[Spec] def shiftedTestedSpan(offset: Long, mock: MockSpan): TestedSpan = TestedSpan(
    traceId       = mock.context.traceId - offset,
    spanId        = mock.context.spanId - offset,
    parentId      = if (mock.parentId == 0) 0 else mock.parentId - offset,
    operationName = mock.operationName,
    tags          = mock.tags.asScala.toMap,
    logs          = mock.logEntries().asScala.map(_.fields().asScala.toMap).toList
  )

  private[Spec] lazy val mockSpanNextIdField = {
    val f = classOf[MockSpan].getDeclaredField("nextId")
    f.setAccessible(true)
    f.get(null).asInstanceOf[AtomicLong]
  }
}
