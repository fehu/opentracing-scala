package com.github.fehu.opentracing.propagation

import cats.effect.std.Dispatcher
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.scalatest.Ignore
import org.scalatest.freespec.AnyFreeSpec

import com.github.fehu.opentracing.syntax._
import com.github.fehu.opentracing.{ Spec, Traced }

@Ignore
abstract class PropagationSpec[F[_]: Traced: Sync] extends AnyFreeSpec with Spec {
  def dispatcher: Dispatcher[F]

  "Serialize and deserialize span context through `TextMap` built-in format" in dispatcher.unsafeRunSync {
    for {
      _        <- Sync[F].pure(()).trace("A")
      carrier0 <- Traced.extractContext[F].to[TextMapPropagation]
      repr      = carrier0.fold(Map.empty[String, String])(_.repr)
      carrier1  = TextMapPropagation(repr)
      _        <- Sync[F].pure(()).injectPropagated(carrier1)("B")
    } yield {
      finishedSpans() shouldBe Seq(
        TestedSpan(spanId = 1, parentId = 0, operationName = "A"),
        TestedSpan(spanId = 2, parentId = 1, operationName = "B")
      )
    }
  }

  "Serialize and deserialize span context through `Binary` built-in format" in {
    cancel("MockTracer only supports `TextMap` format.")
  }

}
