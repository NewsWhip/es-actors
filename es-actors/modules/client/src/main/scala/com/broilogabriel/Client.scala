package com.broilogabriel

import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ Actor, ActorSystem, PoisonPill, Props }
import akka.pattern.ask
import akka.util.Timeout
import com.broilogabriel.Reaper.WatchMe
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.transport.TransportClient
import org.joda.time.{ DateTime, DateTimeConstants }
import scopt.OptionParser
import spray.client.pipelining._
import spray.http.{ HttpCharsets, HttpEntity, HttpRequest, HttpResponse, MediaTypes, Uri }

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

object Config {
  val defaultPort = 9300
  val defaultRemotePort = 9087
}

case class Config(index: String = "", indices: Set[String] = Set.empty,
    sourceAddresses: Seq[String] = Seq("localhost"),
    sourcePort: Int = Config.defaultPort, sourceCluster: String = "",
    targetAddresses: Seq[String] = Seq("localhost"),
    targetPort: Int = Config.defaultPort, targetCluster: String = "",
    remoteAddress: String = "127.0.0.1", remotePort: Int = Config.defaultRemotePort,
    remoteName: String = "RemoteServer") {
  def source: ClusterConfig = ClusterConfig(name = sourceCluster, addresses = sourceAddresses, port = sourcePort)

  def target: ClusterConfig = ClusterConfig(name = targetCluster, addresses = targetAddresses, port = targetPort)
}

object Client extends LazyLogging {

  def formatElapsedTime(millis: Long): String = {
    val hours = MILLISECONDS.toHours(millis)
    val minutes = MILLISECONDS.toMinutes(millis)
    val seconds = MILLISECONDS.toSeconds(millis) - MINUTES.toSeconds(minutes)
    f"$hours%02d:${minutes - HOURS.toMinutes(hours)}%02d:$seconds%02d"
  }

  def indicesByWeeks(date: String, weeksBack: String, validate: Boolean = false): Option[Set[String]] = {
    try {
      val weeks = weeksBack.toInt
      val startDate = DateTime.parse(date).minusWeeks(weeks).withDayOfWeek(DateTimeConstants.SUNDAY)
      indicesByRange(startDate.toString, date, validate = validate)
    } catch {
      case _: IllegalArgumentException => None
    }
  }

  def indicesByRange(startDate: String, endDate: String, validate: Boolean = false): Option[Set[String]] = {
    try {
      val sd = DateTime.parse(startDate).withDayOfWeek(DateTimeConstants.SUNDAY)
      logger.info(s"Start date: $sd")
      val ed = DateTime.parse(endDate).withDayOfWeek(DateTimeConstants.SUNDAY)
      logger.info(s"End date: $ed")
      if (sd.getMillis <= ed.getMillis) {
        Some(if (!validate) getIndices(sd, ed) else Set.empty)
      } else {
        None
      }
    } catch {
      case e: IllegalArgumentException => None
    }
  }

  @tailrec
  def getIndices(sDate: DateTime, eDate: DateTime, indices: Set[String] = Set.empty): Set[String] = {
    if (sDate.getMillis > eDate.getMillis) {
      indices
    } else {
      getIndices(sDate.plusWeeks(1), eDate, indices + s"a-${sDate.getWeekyear}-${sDate.getWeekOfWeekyear}")
    }
  }

  def parser: OptionParser[Config] = new OptionParser[Config]("es-client") {
    head(BuildInfo.name, BuildInfo.version)

    opt[Seq[String]]('i', "indices").valueName("<index1>,<index2>...")
      .action((x, c) => c.copy(indices = c.indices ++ x.toSet))
    opt[(String, String)]('d', "dateRange").validate(
      d => if (indicesByRange(d._1, d._2, validate = true).isDefined) success else failure("Invalid dates")
    ).action({
        case ((start, end), c) => c.copy(indices = indicesByRange(start, end).get)
      }).keyValueName("<start_date>", "<end_date>").text("Start date value should be lower than end date.")

    opt[Seq[String]]('s', "sources").valueName("<source_address1>,<source_address2>")
      .action((x, c) => c.copy(sourceAddresses = x)).text("default value 'localhost'")
    opt[Int]('p', "sourcePort").valueName("<source_port>")
      .action((x, c) => c.copy(sourcePort = x)).text(s"default value ${Config.defaultPort}")
    opt[String]('c', "sourceCluster").required().valueName("<source_cluster>")
      .action((x, c) => c.copy(sourceCluster = x))

    opt[Seq[String]]('t', "targets").valueName("<target_address1>,<target_address2>...")
      .action((x, c) => c.copy(targetAddresses = x)).text("default value 'localhost'")
    opt[Int]('r', "targetPort").valueName("<target_port>")
      .action((x, c) => c.copy(targetPort = x)).text(s"default value ${Config.defaultPort}")
    opt[String]('u', "targetCluster").required().valueName("<target_cluster>")
      .action((x, c) => c.copy(targetCluster = x))

    opt[String]("remoteAddress").valueName("<remote_address>").action((x, c) => c.copy(remoteAddress = x))
    opt[Int]("remotePort").valueName("<remote_port>").action((x, c) => c.copy(remotePort = x))
    opt[String]("remoteName").valueName("<remote_name>").action((x, c) => c.copy(remoteName = x))

    opt[Map[String, String]]("nightly").valueName("value name to define")
      .validate(p => {
        if (p.contains("date") && p.contains("weeksBack") &&
          indicesByWeeks(p("date"), p("weeksBack"), validate = true).isDefined) {
          success
        } else {
          failure("You have to define date=<some_date> and weeksBack=<number_of_weeks>")
        }
      })
      .action((x, c) => c.copy(indices = c.indices ++ indicesByWeeks(x("date"), x("weeksBack")).get))

    help("help").text("Prints the usage text.")

    checkConfig(c => if (c.indices.nonEmpty) success else failure("Missing indices. Check help to send index."))
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()) match {
      case Some(config) => init(config)
      case None => logger.info("Try again with the arguments")
    }
  }

  def init(config: Config): Unit = {
    logger.info(s"$config")
    val actorSystem = ActorSystem.create("MigrationClient")
    val reaper = actorSystem.actorOf(Props(classOf[ProductionReaper]))
    logger.info(s"Creating actors for indices ${config.indices}")
    config.indices.foreach(index => {
      val actorRef = actorSystem.actorOf(
        Props(classOf[Client], config.copy(index = index, indices = Set.empty)),
        s"RemoteClient-$index"
      )
      reaper ! WatchMe(actorRef)
    })
  }

}

class WebClient(implicit system: ActorSystem) extends LazyLogging {

  val logRequest: HttpRequest => HttpRequest = { r => logger.debug(r.toString); r }
  val logResponse: HttpResponse => HttpResponse = { r => logger.debug(r.toString); r }

  // create a function from HttpRequest to a Future of HttpResponse
  val pipeline: HttpRequest => Future[HttpResponse] = logRequest ~> sendReceive ~> logResponse

  // create a function to send a GET request and receive a string response
  def post(path: String, json: String, params: Map[String, String]): Future[String] = {
    val uri = Uri("http://localhost:18000/article").withQuery(params)
    // TODO: @broilogabriel check if charset will work
    val request = Post(uri, HttpEntity(MediaTypes.`application/json`.withCharset(HttpCharsets.`UTF-8`), json))
    val futureResponse = pipeline(request)
    futureResponse.map(
      resp => {
        logger.info(s"Success ${resp.status.isSuccess}")
        resp.entity.asString
      }
    )
  }
}

class Client(config: Config) extends Actor with LazyLogging {

  var scroll: SearchResponse = _
  var cluster: TransportClient = _
  var uuid: UUID = _
  val total: AtomicLong = new AtomicLong()
  val wsPort = 18000
  val slide = 100

  val restServiceClient = new WebClient()(context.system)

  implicit val timeout = Timeout(120.seconds)

  override def preStart(): Unit = {
    cluster = Cluster.getCluster(config.source)
    scroll = Cluster.getScrollId(cluster, config.index)
    logger.debug(s"Getting scroll for index ${config.index} took ${scroll.getTookInMillis}ms")
    if (Cluster.checkIndex(cluster, config.index)) {
      val path = s"akka.tcp://MigrationServer@${config.remoteAddress}:${config.remotePort}/user/${config.remoteName}"
      val remote = context.actorSelection(path)
      // TODO: add handshake before start sending data, the server might not be alive and the application is not killed
      remote ! config.target.copy(totalHits = scroll.getHits.getTotalHits)
      logger.info(s"${config.index} - Connected to remote")
    } else {
      logger.info(s"Invalid index ${config.index}")
      self ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    logger.info(s"${uuid.toString} - ${config.index} - Requested to stop.")
    cluster.close()
  }

  override def receive: Actor.Receive = {
    case MORE =>
      logger.debug(s"${sender.path.name} - requesting more")
      val hits = Cluster.scroller(config.index, scroll.getScrollId, cluster)
      if (hits.nonEmpty) {
        hits.sliding(slide).foreach(sublist => {
          logger.info(s"sublist size - ${sublist.length}")
          restServiceClient.post("", sublist.map(_.getSourceAsString).mkString("[", ",", "]"), Map())
          logger.info(s"After sent?")
        })
        hits.foreach(hit => {
          val data = TransferObject(uuid, config.index, hit.getType, hit.getId, hit.getSourceAsString)
          try {
            val serverResponse = Await.result(sender ? data, timeout.duration)
            if (data.hitId != serverResponse) {
              logger.info(s"${sender.path.name} - Expected response: ${
                data
                  .hitId
              }, but server responded with: $serverResponse")
            }
          } catch {
            case _@(_: TimeoutException | _: InterruptedException) =>
              logger.warn(s"${sender.path.name} - Exception  awaiting for $data")
            case e: Exception => logger.error(s"Unexpected Exception: ${e.getMessage}")
          }
        })
        val totalSent = total.addAndGet(hits.length)
        logger.debug(s"${sender.path.name} - ${config.index} - ${
          (totalSent * 100) / scroll.getHits
            .getTotalHits
        }% | Sent $totalSent of ${scroll.getHits.getTotalHits}")
      } else {
        logger.info(s"${sender.path.name} - ${config.index} - Sending DONE")
        sender ! DONE
      }

    case uuidInc: UUID =>
      uuid = uuidInc
      val scrollId = scroll.getScrollId.substring(0, 10)
      logger.debug(
        s"${sender.path.name} - ${config.index} - Scroll $scrollId - ${scroll.getHits.getTotalHits}"
      )
      self.forward(MORE)

    case other =>
      logger.info(s"${sender.path.name} - ${config.index} - Unknown message: $other")
  }

}