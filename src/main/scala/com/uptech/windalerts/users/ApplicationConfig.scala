package com.uptech.windalerts.users

object ApplicationConfig {
  /**
   * Specify the name of your application. If the application name is
   * {@code null} or blank, the application will log a warning. Suggested
   * format is "MyCompany-Application/1.0".
   */
  val APPLICATION_NAME = ""

  /**
   * Specify the package name of the app.
   */
  val PACKAGE_NAME = ""

  /**
   * Authentication.
   * <p>
   * Installed application: Leave this string empty and copy or
   * edit resources/client_secrets.json.
   * </p>
   * <p>
   * Service accounts: Enter the service
   * account email and add your key.p12 file to the resources directory.
   * </p>
   */
  val SERVICE_ACCOUNT_EMAIL = ""

  /**
   * Specify the apk file path of the apk to upload, i.e. /resources/your_apk.apk
   * <p>
   * This needs to be set for running {@link BasicUploadApk} and {@link UploadApkWithListing}
   * samples.
   * </p>
   */
  val APK_FILE_PATH = ""
}