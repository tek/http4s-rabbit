package hr
package simple

import scala.concurrent.ExecutionContext
import cats.instances.all._
import cats.effect.IO
import fs2.{Stream, Sink}
import fs2.async.mutable.Queue
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.json.{Fs2JsonEncoder, Fs2JsonDecoder}
import com.github.gvolpe.fs2rabbit.model.{AmqpMessage, AmqpProperties, AckResult, AmqpEnvelope, Ack}
import io.circe.{Encoder, Decoder}


case class RabbitConnection(acker: StreamAcker[IO], consumer: StreamConsumer[IO], publisher: StreamPublisher[IO])

object ConnectRabbit
{
  def declareQueue(conf: RabbitConf)(implicit R: Fs2Rabbit[IO], channel: AMQPChannel): Stream[IO, Unit] =
    for {
      _ <- R.declareQueue(RabbitConf.declarationConf(conf.queue))
      _ <- R.declareExchange(conf.exchange, ExchangeType.Topic)
      _ <- R.bindQueue(conf.queue, conf.exchange, conf.routingKey)
    } yield ()

  def createComm(rabbit: Fs2Rabbit[IO], conf: RabbitConf, channel: AMQPChannel): Stream[IO, RabbitConnection] = {
    implicit val r: Fs2Rabbit[IO] = rabbit
    implicit val c: AMQPChannel = channel
    for {
      _ <- declareQueue(conf)
      ackerConsumer <- rabbit.createAckerConsumer(conf.queue)
      publisher <- rabbit.createPublisher(conf.exchange, conf.routingKey)
    } yield RabbitConnection(ackerConsumer._1, ackerConsumer._2, publisher)
  }

  def apply(conf: RabbitConf)(implicit ec: ExecutionContext): Stream[IO, RabbitConnection]
  = {
    for {
      rabbit <- Stream.eval(Fs2Rabbit[IO](conf.native))
      channel <- rabbit.createConnectionChannel
      comm <- createComm(rabbit, conf, channel)
    } yield comm
  }
}

object Rabbit
{
  val conf = RabbitConf.cons("test", "test", "test")

  def amqpMessage[A](data: A): AmqpMessage[A] = AmqpMessage(data, AmqpProperties.empty)

  def amqpMessagePipe[A](in: Stream[IO, A]): Stream[IO, AmqpMessage[A]] = in.map(amqpMessage)

  def jsonEncoder = new Fs2JsonEncoder[IO]

  def jsonDecoder = new Fs2JsonDecoder[IO]

  def publish[A: Encoder](conn: RabbitConnection, q: Queue[IO, A]): Stream[IO, Unit] =
    q.dequeue
      .through(amqpMessagePipe)
      .through(jsonEncoder.jsonEncode[A])
      .to(conn.publisher)

  def ack(in: Stream[IO, AmqpEnvelope]): Stream[IO, AckResult] = in.map(a => Ack(a.deliveryTag))

  def string(in: Stream[IO, AmqpEnvelope]): Stream[IO, String] = in.map(_.toString)

  def processMessage[A: Decoder](q: Queue[IO, A])(in: Stream[IO, AmqpEnvelope]): Stream[IO, Unit] =
    in
      .through(jsonDecoder.jsonDecode[A])
      .collect { case (Right(msg), _) => msg }
      .to(q.enqueue)

  def consume[A: Encoder: Decoder]
  (conn: RabbitConnection, q: Queue[IO, A])
  (implicit ec: ExecutionContext)
  : Stream[IO, Unit] =
    conn.consumer
      .observe(ack _ andThen conn.acker)
      .observe(string _ andThen Sink.showLinesStdOut)
      .to(processMessage(q))
}
