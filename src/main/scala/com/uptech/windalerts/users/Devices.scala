package com.uptech.windalerts.users


import java.util

import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, WriteResult}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{DeviceRequest, UserDevice, UserDevices}

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait Devices {
  val devices: Devices.Service
}

object Devices {

  trait Service {
    def delete(owner: String, deviceId: String): IO[Either[RuntimeException, IO[WriteResult]]]

    def getAllForUser(user: String): IO[UserDevices]

    def saveDevice(device: domain.DeviceRequest, getUid: String): IO[UserDevice]
  }

  class FireStoreBackedService(db: Firestore)(implicit cs: ContextShift[IO]) extends Service {
    private val devices: CollectionReference = db.collection("devices")

    override def getAllForUser(user: String): IO[UserDevices] = {
      getAllByQuery(devices.whereEqualTo("owner", user)).map(devices => UserDevices(devices))
    }

    override def saveDevice(device: domain.DeviceRequest, getUid: String): IO[UserDevice] = {
      for {
        _ <- IO.fromFuture(IO(j2s(devices.document(device.deviceId).create(toBean(device, getUid)))))
        saved <- IO(UserDevice(device.deviceId, getUid))
      } yield saved
    }

    override def delete(requester: String, deviceId: String): IO[Either[RuntimeException, IO[WriteResult]]] = {
      val device = getById(deviceId)
      device.map(ud => if (ud.ownerId == requester) Right(delete(deviceId)) else Left(new RuntimeException("Fail")))
    }

    private def delete(deviceId: String): IO[WriteResult] = {
      IO.fromFuture(IO(j2s(devices.document(deviceId).delete())))
    }


    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

    def toBean(device: DeviceRequest, owner: String): DeviceBean = {
      new DeviceBean(owner, device.deviceId)
    }

    private def getById(id: String): IO[UserDevice] = {
      for {
        document <- IO.fromFuture(IO(j2s(devices.document(id).get())))
        device <- IO({
          val UserDevice(device) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
          device
        })
      } yield device
    }

    private def getAllByQuery(query: firestore.Query) = {
      for {
        collection <- IO.fromFuture(IO(j2s(query.get())))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val UserDevice(device) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              device
            }))
      } yield filtered
    }
  }

  class DeviceBean(
                    @BeanProperty var owner: String,
                    @BeanProperty var deviceId: String) {}

}