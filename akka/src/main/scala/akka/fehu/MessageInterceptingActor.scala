package akka.fehu

import akka.actor.Actor

trait MessageInterceptingActor extends Actor {
  override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit = {
    super.aroundReceive(receive, interceptIncoming(msg))
    afterReceive()
  }

  protected def interceptIncoming(message: Any): Any
  protected def afterReceive(): Unit
}
