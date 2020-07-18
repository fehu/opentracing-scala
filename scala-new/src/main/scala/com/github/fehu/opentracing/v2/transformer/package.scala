package com.github.fehu.opentracing.v2

import cats.data.StateT
import cats.effect.IO

import com.github.fehu.opentracing.v2.internal.TracedTTracedInstances

package object transformer extends TracedTTracedInstances {
  import com.github.fehu.opentracing.v2.internal.State

  type TracedT[F[_], A] = StateT[F, State, A]

  type TracedTIO[A] = TracedT[IO, A]
}
