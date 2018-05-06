package hr

import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationQueueConfig, Durable}
import com.github.gvolpe.fs2rabbit.model.{ExchangeName, RoutingKey, QueueName}

case class RabbitConf(native: Fs2RabbitConfig, exchange: ExchangeName, queue: QueueName, routingKey: RoutingKey)

object RabbitConf
{
  val native = Fs2RabbitConfig(
    virtualHost = "sprcom",
    host = "127.0.0.1",
    username = Some("admin"),
    password = Some("admin"),
    port = 5672,
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false
  )

  def cons(exchange: String, queue: String, routingKey: String) = {
    RabbitConf(
      native,
      ExchangeName(exchange),
      QueueName(queue),
      RoutingKey(routingKey),
    )
  }

  def declarationConf(name: QueueName) =
    DeclarationQueueConfig.default(name).copy(durable = Durable)
}
