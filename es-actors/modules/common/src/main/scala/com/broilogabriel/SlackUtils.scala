package com.broilogabriel

import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scalaj.http.{ Http, HttpResponse }


object SlackUtils {
  val r2d2WebhookUrl = "https://hooks.slack.com/services/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"

  def sendMessageToChannel(message: String): HttpResponse[String] = {
    val jsonObj = ("text" -> message) ~ ("icon_emoji" -> ":sneakyval:") ~ ("username" -> "ValBOT")
    Http(r2d2WebhookUrl).postData(compact(jsonObj)).header("content-type", "application/json").asString
  }
}
