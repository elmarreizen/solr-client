package nl.elmar.solr.client.api

import akka.http.scaladsl.model.Uri.Path

class CollectionsApi(solrApi: SolrApi) {
  val path: Path = solrApi.path / "collections"
  def apply(name: String) = new CollectionApi(solrApi, this, name)
}
