package com.broilogabriel

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import org.scalatest.mockito.MockitoSugar

class MockModule(implicit system: ActorSystem) extends AbstractModule with MockitoSugar with AkkaGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[ActorSystem]).toInstance(system)
    bind(classOf[BaseConfig]).toInstance(mock[BaseConfig])
    bind(classOf[Cluster]).toInstance(mock[Cluster])
    bindActorFactory[ClientActor, ClientActor.Factory]
  }
}
