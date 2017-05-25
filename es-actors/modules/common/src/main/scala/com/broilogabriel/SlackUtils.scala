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
