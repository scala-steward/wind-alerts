package com.uptech.windalerts.users

import java.io.{File, IOException, InputStreamReader}
import java.security.GeneralSecurityException
import java.util.Collections.singleton

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets, GoogleCredential}
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.client.auth.oauth2.Credential
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Collections

import com.google.api.client.http.HttpTransport
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.androidpublisher.AndroidPublisherScopes


object AndroidPublisherHelper {

  import com.google.api.client.json.jackson2.JacksonFactory


  val MIME_TYPE_APK = "application/vnd.android.package-archive"

  /** Path to the private key file (only used for Service Account auth). */
  private val SRC_RESOURCES_KEY_P12 = "src/main/resources/key.p12"

  /**
   * Path to the client secrets file (only used for Installed Application
   * auth).
   */
  private val RESOURCES_CLIENT_SECRETS_JSON = "/resources/client_secrets.json"

  /**
   * Directory to store user credentials (only for Installed Application
   * auth).
   */
  private val DATA_STORE_SYSTEM_PROPERTY = "user.home"
  private val DATA_STORE_FILE = ".store/android_publisher_api"
  private val DATA_STORE_DIR = new File(System.getProperty(DATA_STORE_SYSTEM_PROPERTY), DATA_STORE_FILE)

  /** Global instance of the JSON factory. */
  private val JSON_FACTORY = JacksonFactory.getDefaultInstance

  /** Global instance of the HTTP transport. */
  private var HTTP_TRANSPORT:HttpTransport = null

  /** Installed application user ID. */
  private val INST_APP_USER_ID = "user"

  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to
   * make it a single globally shared instance across your application.
   */
  private var dataStoreFactory:FileDataStoreFactory  = null

  @throws[GeneralSecurityException]
  @throws[IOException]
  private def authorizeWithServiceAccount(serviceAccountEmail: String) = {
    new GoogleCredential
      .Builder()
      .setTransport(HTTP_TRANSPORT)
      .setJsonFactory(JSON_FACTORY)
      .setServiceAccountId(serviceAccountEmail)
      .setServiceAccountScopes(singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
      .setServiceAccountPrivateKeyFromP12File(new File(SRC_RESOURCES_KEY_P12)).build

  }

  /**
   * Authorizes the installed application to access user's protected data.
   *
   * @throws IOException
   * @throws GeneralSecurityException
   */
  @throws[IOException]
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

  /**
   * Ensure the client secrets file has been filled out.
   *
   * @param clientSecrets the GoogleClientSecrets containing data from the
   *                      file
   */
  private def checkClientSecretsFile(clientSecrets: GoogleClientSecrets): Unit = {
    if (clientSecrets.getDetails.getClientId.startsWith("[[INSERT") || clientSecrets.getDetails.getClientSecret.startsWith("[[INSERT")) {
      System.exit(1)
    }
  }

  import com.google.api.services.androidpublisher.AndroidPublisher

  /**
   * Performs all necessary setup steps for running requests against the API
   * using the Installed Application auth method.
   *
   * @param applicationName the name of the application: com.example.app
   * @return the { @Link AndroidPublisher} service
   */
  @throws[Exception]
  protected def init(applicationName: String): AndroidPublisher = init(applicationName, null)

  import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
  import com.google.api.services.androidpublisher.AndroidPublisher
  import java.io.IOException
  import java.security.GeneralSecurityException

  /**
   * Performs all necessary setup steps for running requests against the API.
   *
   * @param applicationName     the name of the application: com.example.app
   * @param serviceAccountEmail the Service Account Email (empty if using
   *                            installed application)
   * @return the { @Link AndroidPublisher} service
   * @throws GeneralSecurityException
   * @throws IOException
   */
  @throws[IOException]
  @throws[GeneralSecurityException]
  def init(applicationName: String, serviceAccountEmail: String): AndroidPublisher = {
    // Authorization.
    newTrustedTransport()
    var credential:Credential = null
    if (serviceAccountEmail == null || serviceAccountEmail.isEmpty) credential = authorizeWithInstalledApplication
    else credential = authorizeWithServiceAccount(serviceAccountEmail)
    // Set up and return API client.
    new AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(applicationName).build
  }

  @throws[GeneralSecurityException]
  @throws[IOException]
  private def newTrustedTransport(): Unit = {
    if (null == HTTP_TRANSPORT) HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport
  }
}
