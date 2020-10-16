package com.github.fehu.opentracing

import _root_.akka.actor.ActorRef
import _root_.akka.util.Timeout
import cats.effect.{ Async, ContextShift }

package object akka {

  def ask[F[_]: Async: ContextShift: Traced](actorRef: ActorRef, message: Any, sender: ActorRef)
                                            (implicit timeout: Timeout): AskTracing.Ops[F] =
    new AskTracing.Ops[F](actorRef, message, sender)

  def ask[F[_]: Async: ContextShift: Traced](actorRef: ActorRef, message: Any)
                                            (implicit timeout: Timeout, sender: ActorRef): AskTracing.Ops[F] =
    new AskTracing.Ops[F](actorRef, message, sender)

}
