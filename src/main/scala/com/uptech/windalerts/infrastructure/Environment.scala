package com.uptech.windalerts.infrastructure

import cats.Applicative
import cats.effect.IO
import org.mongodb.scala.MongoDatabase

case class Environment(db: MongoDatabase)

object Environment {
  trait ApplicativeAsk[F[_], E] extends Serializable {
    val applicative: Applicative[F]

    def ask: F[E]

    def reader[A](f: E => A): F[A]
  }

  type EnvironmentAsk[F[_]] = ApplicativeAsk[F, Environment]

  class EnvironmentIOAsk(env: Environment) extends ApplicativeAsk[IO, Environment] {
    override val applicative: Applicative[IO] = Applicative[IO]

    override def ask: IO[Environment] = applicative.pure(env)

    override def reader[A](f: Environment => A): IO[A] = ask.map(f)
  }
}