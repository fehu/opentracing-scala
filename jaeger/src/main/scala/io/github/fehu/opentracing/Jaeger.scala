package io.github.fehu.opentracing

import cats.effect.Sync
import cats.effect.kernel.Resource
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.internal.samplers.ConstSampler

import io.github.fehu.opentracing.internal.compat.*
import io.github.fehu.opentracing.util.TracerExt

object Jaeger {
  def apply[F[_]: Sync](config: Configuration): Resource[F, TracerExt] =
    Resource.make(Sync[F].delay(TracerExt(config.getTracer.nn)))(t => Sync[F].delay(t.close()))

  def apply[F[_]: Sync](cfg: Configuration => Configuration): Resource[F, TracerExt] =
    apply[F](cfg(Configuration.fromEnv().nn))

  def apply[F[_]: Sync](name: String)(cfg: Configuration => Configuration): Resource[F, TracerExt] =
    apply[F](cfg(Configuration.fromEnv(name).nn))

  def withConstSampler[F[_]: Sync](name: String): Resource[F, TracerExt] =
    apply(name)(
      _.withSampler(
        SamplerConfiguration.fromEnv().nn
          .withType(ConstSampler.TYPE).nn
          .withParam(1).nn
      ).nn
    )
}
