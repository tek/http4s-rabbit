package hr
package generic

import scala.concurrent.ExecutionContext.Implicits.global

import cats.Monad
import cats.syntax.all._
import cats.instances.all._
import cats.effect.{IO, Effect}
import fs2.{Stream, StreamApp, Sink}
import fs2.async.mutable.Queue
import fs2.async.unboundedQueue
import org.http4s.server.blaze.BlazeBuilder
import io.circe.generic.auto._

object Serve
{
  def serve[F[_]: Effect](publish: Msg => F[Unit], consume: F[List[Msg]]): Stream[F, StreamApp.ExitCode] =
    BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(Service.service[F](publish, consume), "/")
      .serve
}

object App
{
  def msgQueue[F[_]: Effect]: Stream[F, Queue[F, Msg]] =
    Stream.eval(Queue.unbounded[F, Msg])

  def app[F[_]: Effect: Monad] = {
    for {
      conn <- ConnectRabbit(Rabbit.conf)
      pubQueue <- msgQueue
      subQueue <- msgQueue
      main <- Stream(
        Rabbit.publish(conn, pubQueue).drain,
        Rabbit.consume(conn, subQueue).drain,
        Serve.serve[F](pubQueue.enqueue1, subQueue.dequeueBatch1(5).map(_.toList)),
        ).joinUnbounded
    } yield main
  }
}
