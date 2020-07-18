package akka.fehu

import scala.util.control.NonFatal

import akka.actor.Actor

trait MessageInterceptingActor extends Actor {
  override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit = {
    try super.aroundReceive(receive, interceptIncoming(msg))
    catch { case NonFatal(err) => afterReceive(Some(err)) }
    afterReceive(None)
  }

  protected def interceptIncoming(message: Any): Any
  protected def afterReceive(maybeError: Option[Throwable]): Unit
}
