package com.uptech.windalerts.infrastructure.social.subscriptions

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets, GoogleCredential}
import com.google.api.client.http.HttpTransport
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.androidpublisher.AndroidPublisherScopes

import java.io.{File, InputStreamReader}
import java.util.Collections
import java.util.Collections.singleton


object AndroidPublisherHelper {

  import com.google.api.client.json.jackson2.JacksonFactory

  private val ENV_RESOURCES_KEY_P12 = "/secrets/androidServiceAccountPrivateKey.p12"

  private val SRC_RESOURCES_KEY_P12 = "src/main/resources/key.p12"

  private val RESOURCES_CLIENT_SECRETS_JSON = "/resources/client_secrets.json"

  private val DATA_STORE_SYSTEM_PROPERTY = "user.home"
  private val DATA_STORE_FILE = ".store/android_publisher_api"
  private val DATA_STORE_DIR = new File(System.getProperty(DATA_STORE_SYSTEM_PROPERTY), DATA_STORE_FILE)

  private val JSON_FACTORY = JacksonFactory.getDefaultInstance

  private var HTTP_TRANSPORT:HttpTransport = null

  private val INST_APP_USER_ID = "user"

  private var dataStoreFactory:FileDataStoreFactory  = null

  import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
  import com.google.api.services.androidpublisher.AndroidPublisher


  def init(applicationName: String, serviceAccountEmail: String): AndroidPublisher = {
    // Authorization.
    newTrustedTransport()
    var credential:Credential = null
    if (serviceAccountEmail == null || serviceAccountEmail.isEmpty) credential = authorizeWithInstalledApplication
    else credential = authorizeWithServiceAccount(serviceAccountEmail)
    // Set up and return API client.
    new AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(applicationName).build
  }

  private def newTrustedTransport(): Unit = {
    if (null == HTTP_TRANSPORT) HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
  }

  private def authorizeWithServiceAccount(serviceAccountEmail: String) = {
    val keyFile = if (new File(ENV_RESOURCES_KEY_P12).exists()) ENV_RESOURCES_KEY_P12 else SRC_RESOURCES_KEY_P12
    new GoogleCredential
      .Builder()
      .setTransport(HTTP_TRANSPORT)
      .setJsonFactory(JSON_FACTORY)
      .setServiceAccountId(serviceAccountEmail)
      .setServiceAccountScopes(singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
      .setServiceAccountPrivateKeyFromP12File(new File(keyFile)).build
  }


  private def authorizeWithInstalledApplication = {
    // load client secrets
    val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(getClass.getResourceAsStream(RESOURCES_CLIENT_SECRETS_JSON)))
    // Ensure file has been filled out.
    checkClientSecretsFile(clientSecrets)
    dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR)
    // set up authorization code flow
    val flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER)).setDataStoreFactory(dataStoreFactory).build
    // authorize
    new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver).authorize(INST_APP_USER_ID)
  }


  private def checkClientSecretsFile(clientSecrets: GoogleClientSecrets): Unit = {
    if (clientSecrets.getDetails.getClientId.startsWith("[[INSERT") || clientSecrets.getDetails.getClientSecret.startsWith("[[INSERT")) {
      System.exit(1)
    }
  }


}
