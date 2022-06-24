package com.uptech.windalerts.infrastructure.beaches

import cats.effect.Resource.eval
import cats.effect.{ConcurrentEffect, ContextShift}
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config.beaches.Beaches
import com.uptech.windalerts.config.config
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.beaches.BeachService
import io.circe.config.parser.decodePathF

object BeachService {
  def apply[F[_]]()(implicit CE: ConcurrentEffect[F], CS: ContextShift[F]) = {
    for {
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = sys.env("WILLY_WEATHER_KEY")

      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))
    } yield beachService
  }
}
