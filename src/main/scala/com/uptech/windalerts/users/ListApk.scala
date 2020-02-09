package com.uptech.windalerts.users

import java.io.IOException
import java.security.GeneralSecurityException
import com.uptech.windalerts.users.AndroidPublisherHelper
import com.uptech.windalerts.users.ApplicationConfig
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import com.google.api.services.androidpublisher.AndroidPublisher

import com.google.api.services.androidpublisher.model.Apk
import com.google.api.services.androidpublisher.model.ApksListResponse
import com.google.api.services.androidpublisher.model.AppEdit


/**
 * Lists all the apks for a given app.
 */
object ListApks {

  def main(args: Array[String]): Unit = {
    try {
      // Create the API service.
      val service = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)
      service
      val subscriptions = service.purchases().subscriptions().acknowledge();

    } catch {
      case ex@(_: IOException | _: GeneralSecurityException) =>
        println("Exception was thrown while updating listing", ex)
    }
  }
}