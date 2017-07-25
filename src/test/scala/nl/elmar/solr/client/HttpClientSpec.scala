package nl.elmar.solr.client

import nl.elmar.solr._
import nl.elmar.solr.client.search._
import nl.elmar.solr.client.update.UpdateRequest
import nl.elmar.solr.request._
import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{JsResult, JsSuccess}

import scala.concurrent.duration.DurationInt

class HttpClientSpec extends SolrSpec {
  val timeout = 5.seconds

  "HttpClient" should {

    "ping solr on localhost" in { implicit ee: ExecutionEnv =>
      httpClient.ping() must be_==(true).awaitFor(timeout)
    }

   "get results with query" in { implicit ee: ExecutionEnv =>
      val query = SearchRequest("collection")
      httpClient.query(query) must beLike[QueryResponse] {
        case response: QueryResponse =>
          response.body.numFound must beEqualTo(0)
      }.awaitFor(timeout)
    }

    "insert documents into solr" in { implicit ee: ExecutionEnv =>
      val schema = Schema(Map(
        "name" -> DataType.String,
        "age" -> DataType.Int
      ))
      import FieldValue._
      val request = UpdateRequest(
        collection = "collection",
        payload = DocumentList(
          Document(
            ("id", Primitive(PrimitiveValue String "1")),
            ("name", Primitive(PrimitiveValue String "Peter")),
            ("age", Primitive(PrimitiveValue String "45"))
          ),
          Document(
            ("id", Primitive(PrimitiveValue String "2")),
            ("name", Primitive(PrimitiveValue String "John")),
            ("age", Primitive(PrimitiveValue String "20"))
          ),
          Document(
            ("id", Primitive(PrimitiveValue String "3")),
            ("name", Primitive(PrimitiveValue String "Ralph")),
            ("age", Primitive(PrimitiveValue String "33"))
          )
        )
      )
      httpClient.update(request) must contain("QTime").awaitFor(timeout)
      httpClient.commit("collection") must beLike[CommitResponse] {
        case CommitResponse(status, queryTime) =>
          status mustEqual 0
      }.awaitFor(timeout)
      val query =
        SearchRequestBody(
          filter = FilterDefinition("age", FilterExpression.Term.String("33")) :: Nil,
          facets =
            FacetDefinition("ages",
              FacetMetadata.Terms("age", subFacets = FacetDefinition("unique", FacetMetadata.Unique("name")) :: Nil)) :: Nil
        )
      httpClient.query(SearchRequest("collection", query)) must beLike[QueryResponse] {
        case response: QueryResponse =>
          response.body.numFound === 1
          response.facets must beSome[ResponseFacets].like {
            case facets => facets.count must equalTo(1L)
          }
          val parser = new QueryResponseParser(response, schema)
          parser.docs must beLike[JsResult[DocumentList]] {
            case JsSuccess(value, _) =>
              value.documents.size === 1
              value.documents.head must beLike[Document] {
                case doc: Document =>
                  doc.fields.get("name") must beSome(Primitive(PrimitiveValue String "Ralph"))
                  doc.fields.get("age") must beSome(Primitive(PrimitiveValue Int 33))
              }
          }
      }.awaitFor(timeout)
    }
  }
}


