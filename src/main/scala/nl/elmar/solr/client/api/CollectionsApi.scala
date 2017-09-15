package nl.elmar.solr.client.api

import akka.http.scaladsl.model.Uri.Path

class CollectionsApi(instance: Instance) {
  val path: Path = instance.path / "collections"
  def apply(name: String) = new CollectionApi(instance, this, name)
}
