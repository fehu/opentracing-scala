# opentracing-scala

#### Common interface for tracing

```scala
trait Interface[Out] {
  def apply(operation: String, tags: Tag*): Out
  def apply(activate: Boolean, operation: String, tags: Tag*): Out
  def apply(parent: Span, operation: String, tags: Tag*): Out
  def apply(parent: Span, activate: Boolean, operation: String, tags: Tag*): Out
  def apply(parent: Option[Span], activate: Boolean, operation: String, tags: Map[String, TagValue]): Out

  def map[R](f: Out => R): Interface[R]
}
```

#### `Tracing` typeclass

`Tracing[F0[_], F1[_]]` defines instruments for building `Interface[F1[A]]` from `F0[A]` input.

There is auto derivation of `Tracing` instances for types that are both `cats.Defer` and `cats.MonadError`, like `EitherT[Eval, Throwable, ?]` or `IO`.


#### `Activating` typeclass

`Activating[F[_]]` activates given span arround execution `F`.

```scala
/** Run `F ~> F` activating given span. */
trait Activating[F[_]] {
  def apply(
    span: Span,
    finishSpanOnClose: Boolean = false,
    onClose: Either[Throwable, Any] => Span => Unit = _ => _ => {}
  ): F ~> F
}
```

There is auto derivation of `Activating` instances for types that are both `cats.Defer` and `cats.MonadError`, like `EitherT[Eval, Throwable, ?]` or `IO`.


### Syntax

#### `trace`

`com.gihub.fehu.opentracing.trace` provides interfaces built from `Tracing[Later, Eval]`.
`trace.now` will execute inmediately while `trace.later` would wrap traced code in `cats.Eval`.

```scala
trace.now("op", "tag" -> "?"){
  // do something
}
```

#### `tracing`

Is provided for input `F0[A]` by an implicit class if there is non-ambiguous `Tracing[F0[A], ?]` instance.

```scala
  IO { ??? }.tracing("IO")
```


#### `activate`
```scala
def activate[R](span: Span, finishSpanOnClose: Boolean = false)(r: => R)(implicit tracer: Tracer): R
```

Defined in package object `com.gihub.fehu.opentracing`, `activate` registers given span with `Tracer`'s scope manager before execution and closes the scope in the end.

#### `activating`
Implicit syntax for types with instances of `Activating`.

```scala
IO { ... }.activating(span)
```

#### logging
Implicit `com.github.fehu.opentracing.SpanOps` defines more logging syntax for `Span`.

## Akka

#### `TracingActor`
The actor stores and processes scopes received inside `TracedMessage`.
The received span can be accessed with `actorSpan()` method (thread usnafe).

Extensions:
- `TracingActor.Activating` 
  The actor activates and closes a scope around its `receive` on receival of `TracedMessage`.
- `TracingActor.ChildSpan`
  The actor creates a child span on receival of `TracedMessage`.
- `TracingActor.ActivatingChildSpan`
  The actor creates a child span, activates and finishes it around its `receive` on receival of `TracedMessage`.
- `TracingActor.AlwaysChildSpan`
  The actor creates a child span for any message, with no parent if the message didn't contain one.

-----------------------

Package `com.github.fehu.opentracing.akka` provides syntax for tracing messages sent to actors.

#### `ask`
```scala
ask(actor, "Hello!")
  .tracing("Introducing", tags = ...)
```

#### `TracingLoggingAdapter`
An re-implementation of akka's `LoggingAdapter` (uses `abstract override`) that sends log enties both to original adapter and currently active span.

  

