package hr

import cats.effect.IO
import fs2.{Stream, StreamApp}


object Main
extends StreamApp[IO]
{
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] =
    simple.App.app
}
