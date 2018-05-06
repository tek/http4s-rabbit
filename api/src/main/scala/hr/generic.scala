package hr
package generic

import cats.Monad
import cats.effect.{IO, Effect}
import cats.syntax.all._
import fs2.Stream
import fs2.text.utf8Encode
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._

object Handler
{
  def publish[F[_]: Monad: Effect](publish: String => Stream[F, Unit], data: String): F[Response[F]] =
    for {
      _ <- publish(data).compile.drain
      _ <- Monad[F].pure(1)
    } yield Response()
}

object GenericRoutes
{
  def apply[F[_]: Monad: Effect](
    publish: Msg => F[Unit],
    consume: F[List[Msg]],
  )
  : PartialFunction[Request[F], F[Response[F]]] = {
    val dsl = Http4sDsl[F]
    import dsl._
    {
      case GET -> Root / "thing" =>
        Ok("hello")
      case Request(GET, Uri(_, _, "/other", _, _), _, _, _, _) =>
        val body = Stream("<html><body><h2>other</h2></body></html>").through(utf8Encode).covary[F]
        Monad[F].pure(Response[F](Status(201, "other"), body = body))
      case POST -> Root / "publish" / a =>
        for {
          _ <- publish(Msg(a))
          response <- Ok(s"published $a")
        } yield response
      case GET -> Root / "consume" =>
        for {
          msgs <- consume
          response <- Ok(msgs.asJson)
        } yield response
    }
  }
}

object Service
{
  def service[F[_]: Effect](publish: Msg => F[Unit], consume: F[List[Msg]]) =
    HttpService[F](GenericRoutes[F](publish, consume))
}
