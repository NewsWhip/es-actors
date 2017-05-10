package com.broilogabriel

import akka.actor.{ Actor, IndirectActorProducer }
import com.google.inject.Injector

class ActorProducer[A <: Actor](injector: Injector, clazz: Class[A]) extends IndirectActorProducer {
  def actorClass: Class[A] = clazz
  def produce(): A = injector.getInstance(clazz)
}
