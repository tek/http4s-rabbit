package hr
package simple

import scala.concurrent.ExecutionContext.Implicits.global

import cats.instances.all._
import cats.effect.IO
import fs2.{Stream, StreamApp, Sink}
import fs2.async.mutable.Queue
import fs2.async.unboundedQueue
import org.http4s.server.blaze.BlazeBuilder
import io.circe.generic.auto._

object Serve
{
  def serve(publish: Msg => IO[Unit], consume: IO[List[Msg]]): Stream[IO, StreamApp.ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(Service.service(publish, consume), "/")
      .serve
}

object App
{
  def msgQueue = Stream.eval(unboundedQueue[IO, Msg])

  def app = {
    for {
      conn <- ConnectRabbit(Rabbit.conf)
      pubQueue <- msgQueue
      subQueue <- msgQueue
      main <- Stream(
        Rabbit.publish(conn, pubQueue).drain,
        Rabbit.consume(conn, subQueue).drain,
        Serve.serve(pubQueue.enqueue1, subQueue.dequeueBatch1(3).map(_.toList)),
        ).joinUnbounded
    } yield main
  }
}
