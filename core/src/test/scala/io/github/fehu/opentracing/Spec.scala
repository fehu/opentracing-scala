package io.github.fehu.opentracing

import scala.collection.JavaConverters.*

import io.opentracing.mock.{ MockSpan, MockTracer }
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, Suite }

import io.github.fehu.opentracing.internal.compat.*

trait Spec extends Matchers with BeforeAndAfter with BeforeAndAfterAll {
  this: Suite =>

  implicit lazy val mockTracer: MockTracer = new MockTracer

  before {
    mockTracer.reset()
  }

  type TestedSpan = Spec.TestedSpan
  val TestedSpan: Spec.TestedSpan.type = Spec.TestedSpan

  def finishedSpans(): Seq[Spec.TestedSpan] = mockTracer.finishedSpans().nn.asScala.toSeq.map(Spec.normalizedSpan)
}

object Spec {
  case class TestedSpan(
      spanId: Long,
      parentId: Long,
      operationName: String,
      tags: Map[String, AnyRef] = Map(),
      logs: List[Map[String, Any]] = Nil
  )

  protected def normalizedSpan(mock: MockSpan): TestedSpan = {
    val traceId = mock.context.nn.traceId
    TestedSpan(
      spanId = mock.context.nn.spanId - traceId,
      parentId = if (mock.parentId == 0) 0 else mock.parentId - traceId,
      operationName = mock.operationName.nn,
      tags = mock.tags.nn.asScala.toMap,
      logs = mock.logEntries().nn.asScala.map(_.fields().nn.asScala.toMap).toList
    )
  }
}
