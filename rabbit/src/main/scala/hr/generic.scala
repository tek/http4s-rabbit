package hr
package generic

import scala.concurrent.ExecutionContext
import cats.instances.all._
import cats.effect.Effect
import fs2.{Stream, Sink}
import fs2.async.mutable.Queue
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.json.{Fs2JsonEncoder, Fs2JsonDecoder}
import com.github.gvolpe.fs2rabbit.model.{AmqpMessage, AmqpProperties, AckResult, AmqpEnvelope, Ack, ExchangeType}
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, StreamAcker, StreamConsumer, StreamPublisher}
import io.circe.{Encoder, Decoder}


case class RabbitConnection[F[_]](acker: StreamAcker[F], consumer: StreamConsumer[F], publisher: StreamPublisher[F])

object ConnectRabbit
{
  def declareQueue[F[_]: Effect](conf: RabbitConf)
  (implicit R: Fs2Rabbit[F], channel: AMQPChannel)
  : Stream[F, Unit] =
    for {
      _ <- R.declareQueue(RabbitConf.declarationConf(conf.queue))
      _ <- R.declareExchange(conf.exchange, ExchangeType.Topic)
      _ <- R.bindQueue(conf.queue, conf.exchange, conf.routingKey)
    } yield ()

  def createComm[F[_]: Effect](rabbit: Fs2Rabbit[F], conf: RabbitConf, channel: AMQPChannel)
  : Stream[F, RabbitConnection[F]] = {
    implicit val r: Fs2Rabbit[F] = rabbit
    implicit val c: AMQPChannel = channel
    for {
      _ <- declareQueue(conf)
      ackerConsumer <- rabbit.createAckerConsumer(conf.queue)
      publisher <- rabbit.createPublisher(conf.exchange, conf.routingKey)
    } yield RabbitConnection(ackerConsumer._1, ackerConsumer._2, publisher)
  }

  def apply[F[_]: Effect](conf: RabbitConf)
  (implicit ec: ExecutionContext)
  : Stream[F, RabbitConnection[F]]
  = {
    for {
      rabbit <- Stream.eval(Fs2Rabbit[F](conf.native))
      channel <- rabbit.createConnectionChannel
      comm <- createComm(rabbit, conf, channel)
    } yield comm
  }
}

object Rabbit
{
  val conf = RabbitConf.cons("test", "test", "test")

  def amqpMessage[F[_], A](data: A): AmqpMessage[A] = AmqpMessage(data, AmqpProperties.empty)

  def amqpMessagePipe[F[_], A](in: Stream[F, A]): Stream[F, AmqpMessage[A]] = in.map(amqpMessage)

  def jsonEncoder[F[_]: Effect] = new Fs2JsonEncoder[F]

  def jsonDecoder[F[_]: Effect] = new Fs2JsonDecoder[F]

  def publish[F[_]: Effect, A: Encoder](conn: RabbitConnection[F], q: Queue[F, A]): Stream[F, Unit] =
    q.dequeue
      .through(amqpMessagePipe)
      .through(jsonEncoder[F].jsonEncode[A])
      .to(conn.publisher)

  def ack[F[_]](in: Stream[F, AmqpEnvelope]): Stream[F, AckResult] = in.map(a => Ack(a.deliveryTag))

  def string[F[_]](in: Stream[F, AmqpEnvelope]): Stream[F, String] = in.map(_.toString)

  def processMessage[F[_]: Effect, A: Decoder](q: Queue[F, A])(in: Stream[F, AmqpEnvelope]): Stream[F, Unit] =
    in
      .through(jsonDecoder[F].jsonDecode[A])
      .collect { case (Right(msg), _) => msg }
      .to(q.enqueue)

  def consume[F[_]: Effect, A: Decoder: Encoder]
  (conn: RabbitConnection[F], q: Queue[F, A])
  (implicit ec: ExecutionContext)
  : Stream[F, Unit] =
    conn.consumer
      .observe(ack[F] _ andThen conn.acker)
      .observe(string[F] _ andThen Sink.showLinesStdOut)
      .to(processMessage(q))
}
