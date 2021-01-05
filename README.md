# opentracing-scala

Functional interface for opentracing and an implementation, based on `cats.data.StateT`.

--------------------------------

Tracing monad is defined over type constructor `T[_[*], *]`, that wraps the underlying monad.
But its interface is separated into two typeclasses.

`Traced[F[_]]` ignores underlying type constructor and defines most operations:
```scala
  def apply[A](op: String, tags: Traced.Tag*)(fa: F[A]): F[A]
  def spanResource(op: String, tags: Traced.Tag*): Resource[F, ActiveSpan]
    
  def pure[A](a: A): F[A]
  def defer[A](fa: => F[A]): F[A]
  
  def currentSpan: Traced.SpanInterface[F]
  
  def injectContext(context: SpanContext): Traced.Interface[F]
  def injectContextFrom[C](carrier: C, format: Format[C]): Traced.Interface[F]
  
  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): F[Option[C0]] 
```

`Traced2[T[_[*], *], F[_]]` refines operations, that require knowing underlying functor:
```scala
  def currentRunParams: F[U, Traced.RunParams]
  def run[A](traced: F[U, A], params: Traced.RunParams): U[A]
  
  def lift[A](ua: U[A]): F[U, A]
  def mapK[G[_]](f: U ~> G): F[U, *] ~> F[G, *]
``` 

### Traced Transformer

Implementation using cats' state transformer.
```scala
  type TracedT[F[_], A] = StateT[F, State, A]
```

Provides (experimental) instances of `Sync` and `ConcurrentEffect`.

# opentracing-jaeger-scalac-implicits

**Compiler plugin** that traces _implicit searches_ performed by scalac
and reports them to local jaegertracing backend.


#### Usage 
- Put to your `build.sbt`
    ```sbtshell
    addCompilerPlugin("com.github.fehu" %% "opentracing-jaeger-scalac-implicits" % "0.1.2")
    ```
- Run, for example, [all-in-one](https://www.jaegertracing.io/docs/1.8/getting-started/#all-in-one) jaeger backend with docker
- Compile your project
- See the traces at http://localhost:16686
