package com.uptech.windalerts.notifications

import java.io.FileInputStream

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.messaging.{FirebaseMessaging, Message}

object TestNotifications extends App {
  val creds = GoogleCredentials.fromStream(new FileInputStream(s"src/main/resources/surfsup-261821.json"))
  val options = new FirebaseOptions.Builder().setCredentials(creds).setProjectId("surfsup-261821").build
  FirebaseApp.initializeApp(options)
  val msg = Message.builder()
    .setNotification(new com.google.firebase.messaging.Notification("title", "body"))
    .setToken("cNYen6LAtkY:APA91bGKKOV7lzAd0i1L8F5ZW8--0u2r55wcZkHsrZPVPruK1VK-0rYtquOeMbWnUUfdZaH54s2bKblpS3knWnVDJ53QlXX049xDem1xf6FIvxJMlS5B73fZqQuKDxT2okJixIzhif6g")
    .build()
  val x = FirebaseMessaging.getInstance.send(msg)
  println(x)
}
