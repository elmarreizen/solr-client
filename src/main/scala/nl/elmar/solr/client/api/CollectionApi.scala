package nl.elmar.solr.client.api

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import nl.elmar.solr.client.search.SearchRequest

class CollectionApi(instance: Instance, collectionsApi: CollectionsApi, name: String) {
  val path: Path = collectionsApi.path / name
  def search: Endpoint[SearchRequest] = {
    body =>
      HttpRequest(
        method = HttpMethods.POST,
        uri = instance.uri,
        entity =
          HttpEntity(
            ContentTypes.`application/json`,
            SearchRequest.bodyWriter.writes(body).toString()
          )
      )
  }
}
