//package com.uptech.windalerts.status
//
//import java.util.UUID
//
//import cats._
//import cats.data.OptionT
//import cats.effect.{IO, Sync}
//import cats.implicits._
//import org.http4s.{HttpRoutes, HttpService}
//import org.http4s.dsl.io._
//import tsec.authentication._
//import tsec.authorization._
//import tsec.cipher.symmetric.jca._
//import tsec.common.SecureRandomId
//import tsec.jws.mac.JWTMac
//import tsec.mac.jca.{HMACSHA256, MacSigningKey}
//
//import scala.collection.mutable
//import scala.concurrent.duration._
//
//object TSec {
//  final case class TSecJWTSettings(
//                                    headerName: String = "X-TSec-JWT",
//                                    expirationTime: FiniteDuration,
//                                    maxIdle: Option[FiniteDuration]
//                                  )
//
//  def dummyBackingStore[F[_], I, V](getId: V => I)(implicit F: Sync[F]) = new BackingStore[F, I, V] {
//    private val storageMap = mutable.HashMap.empty[I, V]
//
//    def put(elem: V): F[V] = {
//      val map = storageMap.put(getId(elem), elem)
//      if (map.isEmpty)
//        F.pure(elem)
//      else
//        F.raiseError(new IllegalArgumentException)
//    }
//
//    def get(id: I): OptionT[F, V] =
//      OptionT.fromOption[F](storageMap.get(id))
//
//    def update(v: V): F[V] = {
//      storageMap.update(getId(v), v)
//      F.pure(v)
//    }
//
//    def delete(id: I): F[Unit] =
//      storageMap.remove(id) match {
//        case Some(_) => F.unit
//        case None => F.raiseError(new IllegalArgumentException)
//      }
//  }
//
//  sealed case class Role(roleRepr: String)
//
//  object Role extends SimpleAuthEnum[Role, String] {
//
//    val Administrator: Role = Role("Administrator")
//    val Customer: Role = Role("User")
//    val Seller: Role = Role("Seller")
//
//    implicit val E: Eq[Role] = Eq.fromUniversalEquals[Role]
//
//    def getRepr(t: Role): String = t.roleRepr
//
//    protected val values: AuthGroup[Role] = AuthGroup(Administrator, Customer, Seller)
//  }
//
//  case class User(id: Int, age: Int, name: String, role: Role = Role.Customer)
//
//  object User {
//    implicit def authRole[F[_]](implicit F: MonadError[F, Throwable]): AuthorizationInfo[F, Role, User] =
//      new AuthorizationInfo[F, Role, User] {
//        def fetchInfo(u: User): F[Role] = F.pure(u.role)
//      }
//  }
//
//  val bearerTokenStore =
//    dummyBackingStore[IO, SecureRandomId, TSecBearerToken[Int]](s => SecureRandomId.coerce(s.id))
//
//  val jwtStore =
//    dummyBackingStore[IO, SecureRandomId, AugmentedJWT[HMACSHA256, Int]](s => SecureRandomId.coerce(s.id))
//
//  val signingKey: MacSigningKey[HMACSHA256] = HMACSHA256.generateKey[Id]
//
//
//  val jwtStatefulAuth =
//    JWTAuthenticator.backed.inBearerToken(
//      expiryDuration = 10.minutes, //Absolute expiration time
//      maxIdle        = None,
//      tokenStore     = jwtStore,
//      identityStore  = userStore,
//      signingKey     = signingKey
//    )
//
//
//  //We create a way to store our users. You can attach this to say, your doobie accessor
//  val userStore: BackingStore[IO, Int, User] = dummyBackingStore[IO, Int, User](_.id)
//
//  val settings: TSecTokenSettings = TSecTokenSettings(
//    expiryDuration = 10.minutes, //Absolute expiration time
//    maxIdle = None
//  )
//
//  val bearerTokenAuth =
//    BearerTokenAuthenticator(
//      bearerTokenStore,
//      userStore,
//      settings
//    )
//
//  val authservice: TSecAuthService[TSecBearerToken[Int], User, IO] = TSecAuthService {
//    case GET -> Root asAuthed user =>
//      Ok()
//  }
//
//  /*
// Now from here, if want want to create services, we simply use the following
// (Note: Since the type of the service is HttpService[IO], we can mount it like any other endpoint!):
//  */
//  val Auth = SecuredRequestHandler(jwtStatefulAuth)
//
//  /*
// Now from here, if want want to create services, we simply use the following
// (Note: Since the type of the service is HttpService[IO], we can mount it like any other endpoint!):
//  */
//  val service: HttpRoutes[IO] = Auth.liftService(TSecAuthService {
//    //Where user is the case class User above
//    case request@GET -> Root / "api" asAuthed user =>
//      /*
//      Note: The request is of type: SecuredRequest, which carries:
//      1. The request
//      2. The Authenticator (i.e token)
//      3. The identity (i.e in this case, User)
//       */
//      val r: SecuredRequest[IO, User, AugmentedJWT[HMACSHA256, Int]] = request
//      Ok(r.identity.name)
//  })
//
//
//}
