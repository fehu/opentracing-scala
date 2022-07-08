# opentracing-scala

Functional interface for opentracing and its implementation, based on `cats.data.StateT`.

# `opentracing-scala-core`

Requires `cats-effect` **3**.

Supports scala `2.12`, `2.13` and `3.1`.

### `Traced[F[_]]` typeclass
[Code](core/src/main/scala/io/github/fehu/opentracing/Traced.scala)

- Lifting values and effects through `pure` and `defer`.
- Creating new spans (`extends Traced.Interface[F]`)
```scala
    def apply[A](op: String, tags: Traced.Tag*)(fa: F[A]): F[A]
    def spanResource(op: String, tags: Traced.Tag*): Resource[F, ActiveSpan]

    def withParent(span: ActiveSpan | SpanContext): Interface[F]
    def withoutParent: Interface[F]
```
- Accessing `currentSpan` through `Traced.SpanInterface[F]`
```scala
    def context: F[Option[SpanContext]]

    def setOperation(op: String): F[Unit]
    def setTag(tag: Traced.Tag): F[Unit]
    def setTags(tags: Traced.Tag*): F[Unit]

    def log(fields: (String, Any)*): F[Unit]
    def log(event: String): F[Unit]

    def setBaggageItem(key: String, value: String): F[Unit]
    def getBaggageItem(key: String): F[Option[String]]
```
- Setting current span
```scala
  def forceCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]
  /** Sets `active` span if no other is set. */
  def recoverCurrentSpan(active: Traced.ActiveSpan): F[Traced.SpanInterface[F]]
```
- Transferring span context
```scala
  def injectContext(context: SpanContext): Traced.Interface[F]
  def injectContextFrom(carrier: Propagation#Carrier): Traced.Interface[F]
  def extractContext[C <: Propagation#Carrier](carrier: C): F[Option[C]]
```

### `Traced2[T[_[_], _], F[_]]` typeclass
`extends Traced[T[F, _]]`

Defines tracing over transformer `T[F, _]`, allowing to run and lift `T[F, _] <~> F`
```scala
  def currentRunParams: T[F, Traced.RunParams]
  def run[A](traced: T[F, A], params: Traced.RunParams): F[A]

  def lift[A](ua: F[A]): T[F, A]
  def mapK[G[_]](f: F ~> G): T[F, _] ~> T[G, _]
```

**Running** the transformer requires
- currently active span
- setup
  - tracer
  - `beforeStart`, `justAfterStart`, `beforeStop` hooks (`Traced.Hooks`)
  - error logger

Default setups can be obtained at `Traced.Setup.default(_: Tracer)` or
- `Jaeger` (in `opentracing-scala-jaeger`) module
- `NoOp` (in `opentracing-scala-noop`) module

### Propagation
Support for
- `BinaryPropagation`
- `TextMapPropagation`

### Syntax
`import io.github.fehu.opentracing.syntax._`

_TODO_

## `TracedT[F[_], A]`
Traced transformer implementation with underlying `cats.data.StateT`.

Provides instances of most `cats.effect` typeclasses and a `Dispatcher` for `TracedT[IO, A]`.

# `opentracing-scala-*`
Other modules:
- `opentracing-scala-akka` - `TracingActor` and `ask(actor, message).trace(...)` syntax.
- `opentracing-scala-fs2` - Syntax extensions for fs2 at `io.github.fehu.opentracing.syntax.FS2`.
- `opentracing-scala-jaeger` - Setup helper for `io.jaegertracing`.
- `opentracing-scala-noop` - No-op setup.

# `opentracing-jaeger-scalac-implicits` (Scala 2 only)

**Compiler plugin** that traces _implicit searches_ performed by scalac
and reports them to local jaegertracing backend.


#### Usage
- Put to your `build.sbt`
    ```sbtshell
    addCompilerPlugin("io.github.fehu" %% "opentracing-jaeger-scalac-implicits" % "0.1.3")
    ```
- Run, for example, [all-in-one](https://www.jaegertracing.io/docs/latest/getting-started/#all-in-one) jaeger backend with docker
- Compile your project
- See the traces at http://localhost:16686
