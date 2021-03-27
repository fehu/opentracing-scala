package com.github.fehu.opentracing.transformer

import _root_.monix.eval.Task

package object monix {
  type TracedTask[A] = TracedT[Task, A]
}
