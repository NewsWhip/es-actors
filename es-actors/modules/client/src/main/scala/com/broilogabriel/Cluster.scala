package com.broilogabriel

import java.net.InetAddress

import com.google.inject.Inject
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

class Cluster @Inject() (transportClient: TransportClient) extends LazyLogging {

  def getCluster(cluster: ClusterConfig): TransportClient = {
    val settings = Settings.settingsBuilder().put("cluster.name", cluster.name)
      .put("client.transport.sniff", true).put("client.transport.ping_timeout", "60s").build()
    val transportClient = TransportClient.builder().settings(settings).build()
    cluster.addresses foreach {
      (address: String) =>
        logger.info(s"Client connecting to $address")
        transportClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(address), cluster.port))
        logger.info(s"Here client $transportClient")
    }

    transportClient
  }

  def checkIndex(index: String): Boolean = {
    transportClient.admin().indices().prepareExists(index)
      .execute().actionGet().isExists
  }

  def getScrollId(index: String, size: Int = ClusterConfig.scrollSize): SearchResponse = {
    transportClient.prepareSearch(index)
      .addSort(SortParseElement.DOC_FIELD_NAME, SortOrder.ASC)
      .setScroll(TimeValue.timeValueMinutes(ClusterConfig.minutesAlive))
      .setQuery(QueryBuilders.matchAllQuery)
      .setSize(size)
      .execute().actionGet()
  }

  def scroller(index: String, scrollId: String): Array[SearchHit] = {
    val partial = transportClient.prepareSearchScroll(scrollId)
      .setScroll(TimeValue.timeValueMinutes(ClusterConfig.minutesAlive))
      .execute()
      .actionGet()
    logger.debug(s"Getting scroll for index ${index} took ${partial.getTookInMillis}ms")
    partial.getHits.hits()
  }

  def close(): Unit = {
    transportClient.close()
  }

}