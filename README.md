This app showcases how to use [fs2] based libs together in an awesome way to run an http service with rabbitmq
communication.
It uses [http4s] and [fs2-rabbit] to provide an http api that allow publishing and consuming from a rabbit queue.
There are two separate implementations: one is specialized to `cats.effect.IO` while the other allows any type, like
`Future`, to handle effects.
They live, respectively, in the `simple.scala` and `generic.scala` files in each dir.

# Separation of concerns into build units
There are four separate sbt subprojects:
* `core` for business logic
* `rabbit`
* `api` for http4s
* `app` for the combination of the others into a runnable app

This principle makes development and maintenance much more bearable and reliable.

* if there is an error in `core`, compilation will stop before descending into the dependent projects
* unit tests can be paired with the tested code in a minimal way
* not having access to IO concerns in the business logic forces the dev to design orthogonally
* concerns are easily replaceable and mockable

# No classes
The variant of classes I'm talking about here excludes data types (case class), algebraic data types
(sealed trait + case classes) and helper classes (to deal with syntax limitations).
Except maybe for IO implementation internals, classes are very toxic for modularity, readability and reasoning.
Even with very careful consideration, e.g. only using them to group parameter dependencies, classes will inevitably
result in less readable code, especially after hacking an ad-hoc feature into an existing class because it would have
needed a rewrite because of changed dependencies.
One particularly malicious variant is the unsealed trait with abstract values.

These shared dependencies are the main reason classes are used in FP at all (They are used a lot for OO data modeling
in our ecosystem, i.e. payment service).
The motivation there is a fear of too many function parameters, especially if they have to be passed around through many
nested levels.
This concern is quite valid, but can very often be mitigated by alternative design approaches.
Many parameters are often a sign that there are too far reaching dependencies in a computation chain and can sometimes
be compensated for by grouping them into data types.

I do not want to advocate the complete abandonment of classes, but I strongly encourage to
* do not be afraid of functions and parameters
* use as many data types as you can

# http4s
Unlike Play and akka-http, http4s comes without a framework.
That means that there are no mixin traits and routing DSL blocks used for constructing routes. Those tend to result in
routing that is rigidly tailored to the business data, which makes maintenance more difficult.

Instead, you only supply a function `Request => IO[Response]` to http4s. Everything else is up to you, which is
especially nice when working on microservices that do not have any special requirements for http.

# fs2-rabbit
There's nothing special here, but it seems to be the only non-java lib that allows manual acking of messages, which was
the reason we were running a custom setup.
Since http4s is also based on `fs2` and `cats-effect`, this integrates seamlessly into the project.

[fs2]: https://github.com/functional-streams-for-scala/fs2
[http4s]: https://github.com/http4s/http4s
[fs2-rabbit]: https://github.com/gvolpe/fs2-rabbit
