package com.broilogabriel

import java.nio.file.Files

import org.elasticsearch.action.admin.indices.refresh.RefreshResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder

trait InMemoryES {
  val node = {
    val localDataDir = System.getProperty("user.dir") + "/target"
    val settings = ImmutableSettings.builder()
      .put("cluster.name", "elasticsearch_embedded")
      .put("http.enabled", true)
      .put("http.port", 9200)
      .put("transport.tcp.port", 9300)
      .put("path.data", Files.createTempDirectory("elasticsearch_data_").toFile.toString)
      .put("path.home", localDataDir).build()
    NodeBuilder.nodeBuilder().data(true).settings(settings).node().start()
  }

  def putSomeData(indexName: String, indexType: String, numItems: Int): BulkResponse = {
    val sources = 1 to numItems map { num: Int => generateJson(num) }
    val indexRequests = sources.map {
      source => new IndexRequest(indexName, indexType).source(source)
    }
    val bulkRequest = node.client().prepareBulk()
    bulkRequest.setRefresh(true)
    indexRequests.foreach {
      indexRequest => bulkRequest.add(indexRequest)
    }
    bulkRequest.execute().actionGet()
  }

  def createIndex(indexName: String): RefreshResponse = {
    node.client().admin().indices().prepareCreate(indexName).execute().actionGet()
    node.client().admin().indices().prepareRefresh().execute().actionGet()
  }

  def deleteAllData(): Unit = {
    node.client().admin().indices().prepareDelete("*").execute().actionGet()
  }

  def generateJson(num: Int): String = {
    val json =
      "{\"testField\": \"testValue\", \"testField2\": \"testValue2\", \"num\" : " + num + "}"
    json
  }
}
