package com.broilogabriel

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Terminated
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ArrayBuffer

object Reaper {

  case class WatchMe(ref: ActorRef)

}

abstract class Reaper extends Actor {

  import Reaper._

  // Keep track of what we're watching
  val watched: ArrayBuffer[ActorRef] = ArrayBuffer.empty[ActorRef]

  // Derivations need to implement this method.  It's the hook that's called when everything's dead
  def allSoulsReaped(): Unit

  // Watch and check for termination
  final def receive: Actor.Receive = {
    case WatchMe(ref) =>
      context.watch(ref)
      watched += ref
    case Terminated(ref) =>
      watched -= ref
      if (watched.isEmpty) allSoulsReaped()
  }
}

class ProductionReaper extends Reaper with LazyLogging {
  // Shutdown
  def allSoulsReaped(): Unit = {
    SlackUtils.sendMessageToChannel("Data transfer to Analytics has completed successfully.")
    logger.info("all done, reaping souls")
    context.system.terminate()
  }
}
