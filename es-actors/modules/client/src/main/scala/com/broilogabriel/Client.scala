package com.broilogabriel

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ Actor, ActorLogging, ActorSystem, PoisonPill, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.Timeout
import com.broilogabriel.Reaper.WatchMe
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.transport.TransportClient
import org.joda.time.{ DateTime, DateTimeConstants }
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.{ DefaultFormats, _ }
import scopt.OptionParser

import scala.annotation.tailrec
import scala.concurrent.duration._

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

//class WebClient(implicit system: ActorSystem) extends LazyLogging {
//
//  val logRequest: HttpRequest => HttpRequest = { r => logger.debug(r.toString); r }
//  val logResponse: HttpResponse => HttpResponse = { r => logger.debug(r.toString); r }
//
//  // create a function from HttpRequest to a Future of HttpResponse
//  val pipeline: HttpRequest => Future[HttpResponse] = logRequest ~> sendReceive ~> logResponse
//
//  // create a function to send a GET request and receive a string response
//  def post(path: String, json: String, params: Map[String, String]): Future[String] = {
//    val uri = Uri("http://localhost:8080/article").withQuery(params)
//    val request = Post(uri, HttpEntity(MediaTypes.`application/json`.withCharset(HttpCharsets.`UTF-8`), json))
//    pipeline(request).map(
//      resp => {
//        logger.info(s"Success ${resp.status.isSuccess}")
//        resp.entity.asString
//      }
//    )
//  }
//}

class Client(config: Config) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  var scroll: SearchResponse = _
  var cluster: TransportClient = _
  var uuid: UUID = _
  val total: AtomicLong = new AtomicLong()
  val wsPort = 18000
  val slide = 500

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  //  val restServiceClient = new WebClient()(context.system)

  val http = Http(context.system)

  implicit val timeout = Timeout(120.seconds)
  implicit val formats = DefaultFormats

  override def preStart(): Unit = {
    cluster = Cluster.getCluster(config.source)
    scroll = Cluster.getScrollId(cluster, config.index)
    log.debug(s"Getting scroll for index ${config.index} took ${scroll.getTookInMillis}ms")
    if (Cluster.checkIndex(cluster, config.index)) {
      val path = s"akka.tcp://MigrationServer@${config.remoteAddress}:${config.remotePort}/user/${config.remoteName}"
      val remote = context.actorSelection(path)
      // TODO: add handshake before start sending data, the server might not be alive and the application is not killed
      remote ! config.target.copy(totalHits = scroll.getHits.getTotalHits)
      log.info(s"${config.index} - Connected to remote")
    } else {
      log.info(s"Invalid index ${config.index}")
      self ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    log.info(s"${uuid.toString} - ${config.index} - Requested to stop.")
    cluster.close()
  }

  override def receive: Actor.Receive = {

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      val body = Unmarshal(entity).to[String].map(jsonString => {
        parse(jsonString) \\ "response"
      })
      log.info(s"Body: $body")

    //      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
    //        log.info("Got response, body: " + body.utf8String.length)
    //      }
    case resp@HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case MORE =>
      log.debug(s"${sender.path.name} - requesting more")
      val hits = Cluster.scroller(config.index, scroll.getScrollId, cluster)
      if (hits.nonEmpty) {
        hits.sliding(slide).foreach(sublist => {
          val data = sublist.map(hit =>
            ("_source" -> parse(hit.getSourceAsString)) ~ ("_type" -> hit.getType) ~ ("_id" -> hit.getId)).toList
          val request = HttpRequest(
            method = HttpMethods.POST,
            entity = HttpEntity(contentType = ContentTypes.`application/json`, compact(render(data))),
            uri = Uri("http://localhost:8080/article").withQuery(Query("type" -> "elastic"))
          )
          http.singleRequest(request).pipeTo(self)
          //          restServiceClient.post("", compact(render(data)), Map("type" -> "elastic")).andThen {
          //            case Success(response) =>
          //              (parse(response) \\ "response").values.asInstanceOf[List[Map[String, String]]].foreach {
          //                i =>
          //                  val to = TransferObject(uuid, config.index, i("_type"), i("_id"), write(i("_source")))
          //                  try {
          //                    val serverResponse = Await.result(sender ? to, timeout.duration)
          //                    if (to.hitId != serverResponse) {
          //                      logger.info(s"${sender.path.name} - Expected response: ${
          //                        to.hitId
          //                      }, but server responded with: $serverResponse")
          //                    }
          //                  } catch {
          //                    case _@(_: TimeoutException | _: InterruptedException) =>
          //                      logger.warn(s"${sender.path.name} - Exception  awaiting for $to")
          //                    case e: Exception => logger.error(s"Unexpected Exception: ${e.getMessage}")
          //                  }
          //              }
          //            case Failure(e) => logger.error("Error calling the Rest service", e)
          //          } andThen {
          //            case _ =>
          //              val totalSent = total.addAndGet(hits.length)
          //              logger.debug(s"${sender.path.name} - ${config.index} - ${
          //                (totalSent * 100) / scroll.getHits.getTotalHits
          //              }% | Sent $totalSent of ${scroll.getHits.getTotalHits}")
          //          }
        })
      } else {
        log.info(s"${sender.path.name} - ${config.index} - Sending DONE")
        sender ! DONE
      }

    case uuidInc: UUID =>
      uuid = uuidInc
      val scrollId = scroll.getScrollId.substring(0, 10)
      log.debug(
        s"${sender.path.name} - ${config.index} - Scroll $scrollId - ${scroll.getHits.getTotalHits}"
      )
      self.forward(MORE)

    case other =>
      log.info(s"${sender.path.name} - ${config.index} - Unknown message: $other")
  }

}