package io.github.fehu.opentracing

import cats.effect.unsafe.{ IORuntime, IORuntimeConfig }

trait IOSpec {
  protected implicit lazy val ioRuntime: IORuntime = {
    val (compute, compDown) = IORuntime.createDefaultComputeThreadPool(null, threads = ioRuntimeComputeThreads)
    val (blocking, blockDown) = IORuntime.createDefaultBlockingExecutionContext()
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

  protected def ioRuntimeComputeThreads: Int = Math.max(2, Runtime.getRuntime.availableProcessors())
  protected def ioRuntimeConfig: IORuntimeConfig = IORuntimeConfig()
}
