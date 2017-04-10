package com.broilogabriel

import com.broilogabriel.TestTag._
import org.specs2.mutable.Specification

class CommonSpec extends Specification {

  "Common" should {

    "integrate with Cauchy" in {
      1 mustEqual 1
    } tag IntegrationTest

    "function in Church" in {
      1 mustEqual 1
    } tag FunctionalTest

    "measure units" in {
      1 mustEqual 1
    } tag UnitTest

    "do nothing" in {
      1 mustEqual 1
    } tag DisabledTest
  }
}
