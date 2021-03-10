package com.uptech.windalerts.core.alerts

import scala.beans.BeanProperty
import scala.util.control.NonFatal


case class TimeRange(@BeanProperty from: Int, @BeanProperty to: Int) {
  def isWithinRange(hourAndMinutes: Int): Boolean = from <= hourAndMinutes && to > hourAndMinutes
}

object TimeRange {
  def unapply(values: Map[String, Long]): Option[TimeRange] = try {
    Some(new TimeRange(values("from").toInt, values("to").toInt))
  }
  catch {
    case NonFatal(_) => None
  }
}
