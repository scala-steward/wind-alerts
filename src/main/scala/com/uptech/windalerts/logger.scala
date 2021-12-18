package com.uptech.windalerts

import net.logstash.logback.argument.StructuredArguments.kv

object logger {

  import org.slf4j.LoggerFactory

  private val log = LoggerFactory.getLogger(logger.getClass)

  def info(msg: String): Unit = {
    log.warn(msg, kv("severity", "INFO"))
  }

  def warn(msg: String): Unit = {
    log.warn(msg, kv("severity", "WARN"))
  }

  def warn(msg: String, e: Throwable): Unit = {
    log.warn(msg, e, kv("severity", "WARN"))
  }

  def error(error: String, e: Throwable): Unit = {
    log.error(error, e)
  }
}
