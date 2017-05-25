package com.broilogabriel

import org.scalatest.FunSuite

class SlackUtilsTest extends FunSuite {
  test("send message to slack") {
    val resp = SlackUtils.sendMessageToChannel("TEST")
    print(resp.body)
  }
}
