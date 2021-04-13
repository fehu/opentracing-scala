package com.github.fehu.opentracing.transformer

import _root_.monix.eval.Task

object Monix {
  type TracedTask[A] = TracedT[Task, A]
}
