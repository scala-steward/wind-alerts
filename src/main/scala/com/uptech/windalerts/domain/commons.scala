package com.uptech.windalerts.domain

import cats.effect.IO
import io.opencensus.trace.Tracing
import io.opencensus.trace.samplers.Samplers
import org.http4s.{HttpRoutes, Request}

object commons {
  val tracer = Tracing.getTracer

  def tracingService(endPoints: HttpRoutes[IO]) = {
    tracingMiddleware(endPoints)
  }

  def tracingMiddleware(service: HttpRoutes[IO]): HttpRoutes[IO] = cats.data.Kleisli { req: Request[IO] => {
    cleanly(tracer.spanBuilder("EndPoint")
      .setSampler(Samplers.alwaysSample()).startScopedSpan())(_.close())(
      _ => {
        service(req)
      })
      .toOption.get
  }
  }

  def cleanly[A, B](resource: => A)(cleanup: A => Unit)(code: A => B): Either[Exception, B] = {
    try {
      val r = resource
      try {
        Right(code(r))
      } finally {
        cleanup(r)
      }
    }
    catch {
      case e: Exception => Left(e)
    }
  }

}
