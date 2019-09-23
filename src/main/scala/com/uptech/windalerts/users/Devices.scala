package com.uptech.windalerts.users


import java.util

import cats.effect.IO
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.google.firebase.auth.{FirebaseAuth, FirebaseToken}
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.{DeviceRequest, UserDevice}

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


trait Devices {
  val devices: Devices.Service
}



object Devices {

  trait Service {
    def saveDevice(device: Domain.DeviceRequest, getUid: String):IO[Domain.UserDevice]
  }

  class FireStoreBackedService(db:Firestore) extends Service {
    private val devices: CollectionReference = db.collection("devices")

    override def saveDevice(device: Domain.DeviceRequest, getUid: String): IO[Domain.UserDevice] = {
      for {
        document <- IO.fromFuture(IO(j2s(devices.add(toBean(device, getUid)))))
        saved <- IO(UserDevice(device.deviceId, getUid))
      } yield saved
    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

    def toBean(device:DeviceRequest, owner:String): DeviceBean = {
      new DeviceBean(owner, device.deviceId)
    }

  }

  class DeviceBean(
                    @BeanProperty var owner: String,
                    @BeanProperty var deviceId: String) {}


}