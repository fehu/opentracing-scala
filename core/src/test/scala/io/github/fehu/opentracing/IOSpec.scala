package io.github.fehu.opentracing

import cats.effect.unsafe.{ IORuntime, IORuntimeConfig }

import io.github.fehu.opentracing.internal.compat.*

trait IOSpec {
  implicit protected lazy val ioRuntime: IORuntime = {
    val (compute, compDown)    = IORuntime.createDefaultComputeThreadPool(ioRuntime, threads = ioRuntimeComputeThreads)
    val (blocking, blockDown)  = IORuntime.createDefaultBlockingExecutionContext()
    val (scheduler, schedDown) = IORuntime.createDefaultScheduler()

    IORuntime(
      compute,
      blocking,
      scheduler,
      { () =>
        compDown()
        blockDown()
        schedDown()
      },
      ioRuntimeConfig
    )
  }

  protected def ioRuntimeComputeThreads: Int     = Math.max(2, Runtime.getRuntime.nn.availableProcessors())
  protected def ioRuntimeConfig: IORuntimeConfig = IORuntimeConfig()
}
