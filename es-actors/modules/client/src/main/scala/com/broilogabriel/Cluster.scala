package com.broilogabriel

import java.net.InetAddress

import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.search.sort.SortParseElement

object Cluster extends LazyLogging {

  def getCluster(cluster: ClusterConfig): TransportClient = {
    val settings = Settings.settingsBuilder().put("cluster.name", cluster.name)
      .put("client.transport.sniff", true).put("client.transport.ping_timeout", "60s").build()
    val transportClient = TransportClient.builder().settings(settings).build()
    cluster.addresses foreach {
      (address: String) =>
        logger.info(s"Client connecting to $address")
        transportClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(address), cluster.port))
    }
    transportClient
  }

  def checkIndex(cluster: TransportClient, index: String): Boolean = {
    cluster.admin().indices().prepareExists(index)
      .execute().actionGet().isExists
  }

  def getScrollId(cluster: TransportClient, index: String, size: Int = ClusterConfig.scrollSize): SearchResponse = {
    cluster.prepareSearch(index)
      .addSort(SortParseElement.DOC_FIELD_NAME, SortOrder.ASC)
      .setScroll(TimeValue.timeValueMinutes(ClusterConfig.minutesAlive))
      .setQuery(QueryBuilders.matchAllQuery)
      .setSize(size)
      .execute().actionGet()
  }

  def scroller(index: String, scrollId: String, cluster: TransportClient): Array[SearchHit] = {
    val partial = cluster.prepareSearchScroll(scrollId)
      .setScroll(TimeValue.timeValueMinutes(ClusterConfig.minutesAlive))
      .execute()
      .actionGet()
    partial.getHits.hits()
  }

}