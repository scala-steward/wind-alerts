package com.uptech.windalerts
import net.logstash.logback.argument.StructuredArguments.kv

object logger {
  import org.slf4j.LoggerFactory

  private val log = LoggerFactory.getLogger(logger.getClass)

  def info(msg:String): Unit = {
    log.warn(msg,  kv("severity", "INFO"))
  }

  def warn(msg:String): Unit = {
    log.warn(msg,  kv("severity", "WARN"))
  }

  def error(error:String, e:Throwable): Unit = {
    log.warn(error, e,  kv("severity", "ERROR"))
  }
}
