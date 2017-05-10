package com.broilogabriel

import akka.actor.ActorSystem
import com.google.inject.AbstractModule

class ProdModule(implicit system: ActorSystem, config: Config) extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[ActorSystem]).toInstance(system)
    bind(classOf[Config]).toInstance(config)
    bindActorFactory[ClientActor, ClientActor.Factory]
  }
}

