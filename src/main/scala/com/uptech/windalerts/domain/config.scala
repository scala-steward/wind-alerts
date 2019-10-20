package com.uptech.windalerts.domain

case class AppSettings(surfsup:SurfsUp)

case class SurfsUp(willyWeather:WillyWeather)

case class WillyWeather(key:String)
