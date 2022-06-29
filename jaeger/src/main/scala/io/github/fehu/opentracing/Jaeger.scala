package io.github.fehu.opentracing

import cats.effect.Sync
import cats.effect.kernel.Resource
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.internal.samplers.ConstSampler

import io.github.fehu.opentracing.util.TracerExt

object Jaeger {
  def apply[F[_]: Sync](config: Configuration): Resource[F, TracerExt] =
    Resource.make(Sync[F].delay(TracerExt(config.getTracer)))(t => Sync[F].delay(t.close()))

  def apply[F[_]: Sync](cfg: Configuration => Configuration): Resource[F, TracerExt] =
    apply[F](cfg(Configuration.fromEnv()))

  def apply[F[_]: Sync](name: String)(cfg: Configuration => Configuration): Resource[F, TracerExt] =
    apply[F](cfg(Configuration.fromEnv(name)))

  def withConstSampler[F[_]: Sync](name: String): Resource[F, TracerExt] =
    apply(name)(
      _.withSampler(
        SamplerConfiguration.fromEnv()
          .withType(ConstSampler.TYPE)
          .withParam(1)
      )
    )
}
