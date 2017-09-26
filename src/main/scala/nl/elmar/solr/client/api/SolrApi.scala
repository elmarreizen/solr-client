package nl.elmar.solr.client.api

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path

class SolrApi(val uri: Uri) {
  val path: Path = Path / "v2"
  val collections: CollectionsApi = new CollectionsApi(this)
}
