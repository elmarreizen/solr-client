package nl.elmar.solr.client.api

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import nl.elmar.solr.client.search.SearchRequest
import nl.elmar.solr.client.update.{BatchUpdateRequest, UpdateRequest}

class CollectionApi(solrApi: SolrApi, collectionsApi: CollectionsApi, name: String) {
  val path: Path = collectionsApi.path / name

  def search: Endpoint[SearchRequest] = {
    body =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = solrApi.uri.withPath(path / "query"),
        entity =
          HttpEntity(
            ContentTypes.`application/json`,
            SearchRequest.bodyWriter.writes(body).toString()
          )
      )
  }

  def update: Endpoint[UpdateRequest] = {
    case BatchUpdateRequest(documents) =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = solrApi.uri.withPath(path / "update" / "json"),
        entity =
          HttpEntity(
            ContentTypes.`application/json`,
            UpdateRequest.documentListWriter.writes(documents).toString()
          )
      )
  }
}
