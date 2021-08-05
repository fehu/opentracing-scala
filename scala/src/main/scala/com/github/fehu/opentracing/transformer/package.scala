package com.github.fehu.opentracing

import cats.effect.IO

package object transformer {
  type TracedIO[A] = TracedT[IO, A]
}
