package com.github.fehu.opentracing.propagation

import cats.effect.Effect
import cats.effect.syntax.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.scalatest.Ignore
import org.scalatest.freespec.AnyFreeSpec

import com.github.fehu.opentracing.propagation.syntax._
import com.github.fehu.opentracing.syntax._
import com.github.fehu.opentracing.{ Spec, Traced }

@Ignore
abstract class PropagationSpec[F[_]: Traced] extends AnyFreeSpec with Spec {
  implicit val effect: Effect[F]

  "Serialize and deserialize span context through `TextMap` built-in format" in {
    for {
      _        <- Effect[F].pure(()).trace("A")
      carrier0 <- Traced.extractContext[F].to[TextMapPropagation]
      repr      = carrier0.fold(Map.empty[String, String])(_.repr)
      carrier1  = TextMapPropagation(repr)
      _        <- Effect[F].pure(()).injectPropagated(carrier1)("B")
    } yield {
      finishedSpans() shouldBe Seq(
        TestedSpan(traceId = 1, spanId = 2, parentId = 0, operationName = "A"),
        TestedSpan(traceId = 1, spanId = 3, parentId = 2, operationName = "B")
      )
    }
  }.toIO.unsafeRunSync()

  "Serialize and deserialize span context through `Binary` built-in format" in {
    cancel("MockTracer only supports `TextMap` format.")
  }

}
