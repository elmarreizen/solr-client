package nl.elmar.solr.client.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Uri}
import akka.util.ByteString
import nl.elmar.solr.client.update.BatchUpdateRequest
import nl.elmar.solr.{Document, FieldValue}

class CollectionApiSpec extends org.specs2.mutable.Specification {
  "CollectionApi" should {
    "create update request" in {
      val batchUpdate =
        BatchUpdateRequest(
          Document(
            "id" -> FieldValue.Primitive.Long(0l),
            "name" -> FieldValue.Primitive.String("John"),
            "age" -> FieldValue.Primitive.Long(25l),
          ) ::
          Nil
        )
      val solrApi = new SolrApi(Uri("http://localhost:8983"))
      val httpRequest = solrApi.collections("collection").update.toHttpRequest(batchUpdate)

      httpRequest.uri.toString() mustEqual "http://localhost:8983/v2/collections/collection/update/json"
      httpRequest.entity mustEqual
        HttpEntity.Strict(
          ContentTypes.`application/json`,
          ByteString("""[{"id":0,"name":"John","age":25}]"""))
    }
  }
}
