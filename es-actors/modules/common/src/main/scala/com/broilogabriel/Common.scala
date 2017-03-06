package com.broilogabriel

import java.util.UUID

//@SerialVersionUID
object ClusterConfig {
  //Beware of setting this variable any higher, causes memory issues in source cluster
  val scrollSize = 500
  val minutesAlive = 10
  val bulkActions = 1000
  val bulkSizeKb = 128
  val flushIntervalSec = 5
}

case class ClusterConfig(name: String, addresses: Seq[String], port: Int, totalHits: Long = 0)

//@SerialVersionUID
case class TransferObject(uuid: UUID, index: String, hitType: String, hitId: String, source: String)

//@SerialVersionUID(1L)
object MORE extends Serializable

//@SerialVersionUID(2L)
object DONE extends Serializable