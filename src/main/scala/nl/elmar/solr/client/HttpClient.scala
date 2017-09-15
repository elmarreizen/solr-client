package nl.elmar.solr.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import JsonMarshaller._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import nl.elmar.solr.client.search._
import nl.elmar.solr.client.update.UpdateRequest
import nl.elmar.solr.request._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class HttpClient(uri: Uri)(implicit materializer: ActorMaterializer) {
  implicit val ec = materializer.system.dispatcher

  private val http = Http(materializer.system)

  def query(request: SearchRequest): Future[QueryResponse] = {
    val httpRequest = SearchRequest.toHttpRequest(uri, request)

    http.singleRequest(httpRequest)
    .flatMap { response =>
      if (response.status.isSuccess())
        response.entity.toStrict(2.seconds)
      else
      response.entity.toStrict(2.seconds).flatMap( entity =>
        Future.failed(new Exception(entity.data.utf8String))
      )
    }
    .map { entity =>
      val json = Json parse entity.data.utf8String
      json.as(ResponseReader.queryResponseReader)
    }
  }

  def update(request: UpdateRequest): Future[String] = {
    val httpRequest = UpdateRequest.toRequest(uri, request)

    http.singleRequest(httpRequest)
    .flatMap { response =>
      response.entity.toStrict(2.seconds)
    }
    .map(_.data.utf8String)
  }

  def ping(): Future[Boolean] = {
    val request =
      HttpRequest(
        uri = uri
          withPath (uri.path / "admin" / "cores")
          withQuery Uri.Query("q" -> "*:*"))
    http.singleRequest(request).map {
      case HttpResponse(s, h, e, p) => s.isSuccess()
    }
  }

  def commit(collection: String): Future[CommitResponse] = {
    val request =
      HttpRequest(
        uri = uri
          withPath (uri.path / collection / "update")
          withQuery Uri.Query("commit" -> "true", "wt" -> "json"))
    http.singleRequest(request).flatMap( response =>
      Unmarshal(response.entity).to[CommitResponse])
  }
}

object ResponseReader {
  implicit val responseBodyReader = Json.reads[ResponseBody]

  implicit val responseFacetsReader = Reads.JsValueReads.map(ResponseFacets)

  implicit val queryResponseReader: Reads[QueryResponse] = (
    (__ \ "response").read[ResponseBody] and
    (__ \ "facets").readNullable[ResponseFacets]
  )(QueryResponse)
}
