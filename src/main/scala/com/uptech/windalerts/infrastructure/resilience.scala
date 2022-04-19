package com.uptech.windalerts.infrastructure

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig

import java.time.Duration
import java.util.concurrent.Callable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object resilience {
  def  willyWeatherRequestsDecorator[T](callable: Callable[T]) : Future[T] = {
    Future({Decorators.ofCallable(callable)
      .withBulkhead(Bulkhead.ofDefaults("willy-weather-bulkhead"))
      .withCircuitBreaker(CircuitBreaker.ofDefaults("willy-weather-circuit-breaker"))
      .withRetry(Retry.of("willy-weather-retry", new RetryConfig.Builder().maxAttempts(10).intervalFunction(IntervalFunction.ofDefaults()).build()))
      .decorate()
      .call()
  })
  }

}
