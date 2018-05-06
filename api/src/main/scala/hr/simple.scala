package hr
package simple

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._

object Handler
{
  def publish(publish: Msg => IO[Unit], data: String): IO[Response[IO]] =
    for {
      _ <- publish(Msg(data))
      response <- Ok(s"published $data")
    } yield response

  def consume(consume: IO[List[Msg]]): IO[Response[IO]] =
    for {
      msgs <- consume
      response <- Ok(msgs.asJson)
    } yield response
}

object Routes
{
  def routes(publish: Msg => IO[Unit], consume: IO[List[Msg]])
  : PartialFunction[Request[IO], IO[Response[IO]]] = {
    case POST -> Root / "publish" / a => Handler.publish(publish, a)
    case GET -> Root / "consume" => Handler.consume(consume)
  }
}

object Service
{
  def service(publish: Msg => IO[Unit], consume: IO[List[Msg]]): HttpService[IO] =
    HttpService[IO](Routes.routes(publish, consume))

  def inlineService = HttpService[IO] {
    case GET -> Root / "thing" => Ok("hello")
  }
}
