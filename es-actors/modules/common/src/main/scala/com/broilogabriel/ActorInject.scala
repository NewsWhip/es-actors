package com.broilogabriel

import akka.actor.{ Actor, ActorRef, ActorRefFactory, Props }
import com.google.inject.Injector

trait ActorInject {
  protected def injector: Injector

  protected def injectActor(create: => Actor)(implicit factory: ActorRefFactory): ActorRef =
    factory.actorOf(Props(create))

  protected def injectActor(create: => Actor, name: String)(implicit factory: ActorRefFactory): ActorRef =
    factory.actorOf(Props(create), name)

}
