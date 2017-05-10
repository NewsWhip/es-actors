package com.broilogabriel

import java.util.UUID

import akka.actor.{ ActorPath, ActorSystem }
import akka.testkit.{ TestKit, TestProbe }
import com.google.inject.{ AbstractModule, Guice }
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.{ SearchHit, SearchHits }
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }


class ClientActorTest extends TestKit(ActorSystem("test")) with MockitoSugar
  with WordSpecLike with Matchers with BeforeAndAfterAll with ActorInject with LazyLogging {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val mockConfig = mock[Config]
  val mockCluster = mock[Cluster]
  val foo = TestProbe()


  val testModule = new AbstractModule with AkkaGuiceSupport {
    override def configure(): Unit = {
      bind(classOf[ActorSystem]).toInstance(system)
      bind(classOf[BaseConfig]).toInstance(mockConfig)
      bind(classOf[Cluster]).toInstance(mockCluster)
      bind(classOf[ActorPath]).toInstance(foo.ref.path)
      bindActorFactory[ClientActor, ClientActor.Factory]
    }
  }

  val injector = Guice.createInjector(testModule)
  val factory = injector.getInstance(classOf[ClientActor.Factory])
  lazy val client = injectActor(factory("testIndex"))

  "clientActor" should {
    "receive UUID and no hits" in {
      val mockScroll = mock[SearchResponse]
      val mockHits = mock[SearchHits]
      when(mockHits.getTotalHits).thenReturn(5)
      when(mockScroll.getHits).thenReturn(mockHits)
      when(mockCluster.getScrollId(any[String], any[Int])).thenReturn(mockScroll)
      when(mockCluster.checkIndex(any[String])).thenReturn(true)
      val clusterConfig = ClusterConfig("", Seq(), 1)
      when(mockConfig.target).thenReturn(clusterConfig)
      when(mockScroll.getScrollId).thenReturn("abcdefghijkl")
      when(mockCluster.scroller(any[String], any[String])).thenReturn(Array.empty[SearchHit])
      logger.info(client.toString())
      foo.send(client, UUID.randomUUID())
    }
  }

}
