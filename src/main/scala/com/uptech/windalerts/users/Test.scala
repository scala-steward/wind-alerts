package com.uptech.windalerts.users

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserRecord.CreateRequest
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}

object Test extends App {
  val credentials = GoogleCredentials.getApplicationDefault
  val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
  FirebaseApp.initializeApp(options)
  val db = FirestoreClient.getFirestore
  val request = new CreateRequest()
    .setEmail("aman@x.com")
    .setEmailVerified(false)
    .setPassword("password")
    .setDisabled(false)
  FirebaseAuth.getInstance.createUser(request)
}
