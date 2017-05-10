package com.broilogabriel

trait BaseConfig {
  val sourceAddresses: Seq[String]
  val sourcePort: Int
  val sourceCluster: String
  val targetAddresses: Seq[String]
  val targetPort: Int
  val targetCluster: String
  val remoteAddress: String
  val remotePort: Int
  val remoteName: String

  def source: ClusterConfig = ClusterConfig(name = sourceCluster, addresses = sourceAddresses, port = sourcePort)

  def target: ClusterConfig = ClusterConfig(name = targetCluster, addresses = targetAddresses, port = targetPort)
}

case class Config(index: String = "", indices: Set[String] = Set.empty,
    sourceAddresses: Seq[String] = Seq("localhost"),
    sourcePort: Int = PortConfig.defaultSourcePort, sourceCluster: String = "",
    targetAddresses: Seq[String] = Seq("localhost"),
    targetPort: Int = PortConfig.defaultTargetPort, targetCluster: String = "",
    remoteAddress: String = "127.0.0.1", remotePort: Int = PortConfig.defaultRemotePort,
    remoteName: String = "RemoteServer") extends BaseConfig {
}
