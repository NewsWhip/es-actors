package com.broilogabriel

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props }
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest

object Server extends App with LazyLogging {
  logger.info(s"${BuildInfo.name} - ${BuildInfo.version}")
  val actorSystem = ActorSystem.create("MigrationServer")
  val actor = actorSystem.actorOf(Props[Server], name = "RemoteServer")
}

class Server extends Actor with ActorLogging {

  override def receive: Actor.Receive = {

    case cluster: ClusterConfig =>
      val uuid = UUID.randomUUID
      log.info(s"Server received cluster config: $cluster")
      context.actorOf(
        Props(classOf[BulkHandler], cluster),
        name = uuid.toString
      ).forward(uuid)

    case other =>
      log.info(s"Server unknown message: $other")

  }

}

class BulkHandler(cluster: ClusterConfig) extends Actor with ActorLogging {

  val bListener = BulkListener(Cluster.getCluster(cluster), self)
  val bulkProcessor: BulkProcessor = Cluster.getBulkProcessor(bListener).build()
  val finishedActions: AtomicLong = new AtomicLong

  override def postStop(): Unit = {
    log.debug(s"${self.path.name} - Stopping BulkHandler")
    bulkProcessor.flush()
    bListener.client.close()
  }

  var client: ActorRef = _

  override def receive: Actor.Receive = {

    case uuid: UUID =>
      log.info(s"${self.path.name} - Starting")
      client = sender()
      sender ! uuid

    case to: TransferObject =>
      //      log.info(s"Received TO, index: ${to.index}")
      bulkProcessor.add(new IndexRequest(to.index, to.hitType, to.hitId).source(to.source))
      sender ! to.hitId

    case DONE =>
      log.info(s"${self.path.name} - Received DONE, gonna send PoisonPill")
      sender ! PoisonPill

    case finished: Int =>
      val actions = finishedActions.addAndGet(finished)
      log.info(
        s"${self.path.name} - Processed ${(actions * 100) / cluster.totalHits}% $actions of ${cluster.totalHits}"
      )
      if (actions < cluster.totalHits) {
        client ! MORE
      } else {
        self ! PoisonPill
      }

    case other =>
      log.info(s"${self.path.name} - Unknown message: $other")
  }

}
