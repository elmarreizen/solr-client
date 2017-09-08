package nl.elmar.solr.client

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import JsonMarshaller._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import nl.elmar.solr.{Document, DocumentList, FieldValue}
import nl.elmar.solr.request._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class HttpClient(uri: Uri)(implicit materializer: ActorMaterializer) {
  implicit val ec = materializer.system.dispatcher

  private val http = Http(materializer.system)

  def query(request: QueryRequest): Future[QueryResponse] = {
    val httpRequest =
      HttpRequest(
        uri = uri
          withPath uri.path / request.collection / "query",
        entity =
          HttpEntity(
            ContentTypes.`application/json`,
            QueryWriter.toJson(request.query).toString
          )
      )

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
    val httpRequest =
      HttpRequest(
        uri = uri
          withPath uri.path / request.collection / "update",
        entity =
          HttpEntity(
            ContentTypes.`application/json`,
            DocumentListWriter.toJson(request.payload).toString
          )
      )

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

object JsonRenderCommon {
  val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T00:00:00Z'")

  def renderDate(date: LocalDate) = {
    val rendered = date format dateTime
    raw""" "$rendered" """
  }

  implicit class JsPathOps(val jsPath: JsPath) extends AnyVal {
    def writeNonEmptyList[A](implicit AListWriter: Writes[List[A]]) =
      jsPath
        .writeNullable(AListWriter)
        .contramap[List[A]] {
          case Nil => None
          case nonEmpty => Some(nonEmpty)
        }

    def lazyWriteNonEmptyList[A](AListWriter: => Writes[List[A]]) = {
      lazy val writer = writeNonEmptyList(AListWriter)
      OWrites[List[A]](list => writer writes list)
    }
  }
}

object QueryWriter {
  import JsonRenderCommon._

  def renderOrUnrolled(exp: FilterExpression): String = {
    exp match {
      case FilterExpression.OR(left: FilterExpression, right: FilterExpression) =>
        s"${renderOrUnrolled(left)} OR ${renderOrUnrolled(right)}"
      case other: FilterExpression =>
        renderFilterExpression(other)
    }
  }

  def renderAndUnrolled(exp: FilterExpression): String = {
    exp match {
      case FilterExpression.AND(left: FilterExpression, right: FilterExpression) =>
        s"${renderAndUnrolled(left)} AND ${renderAndUnrolled(right)}"
      case other: FilterExpression =>
        renderFilterExpression(other)
    }
  }

  def renderFilterExpression(value: FilterExpression): String = value match {
    case FilterExpression.Term.String(v) => v.replace(":", """\:""")
    case FilterExpression.Term.Long(v) => v.toString
    case FilterExpression.Term.Date(v) => renderDate(v)
    case FilterExpression.Range(fromOpt, toOpt) =>
      val from = fromOpt.map(renderFilterExpression).getOrElse("*")
      val to = toOpt.map(renderFilterExpression).getOrElse("*")
      s"[$from TO $to]"
    case FilterExpression.OR(left, right) =>
      s"(${renderOrUnrolled(left)} OR ${renderOrUnrolled(right)})"
    case FilterExpression.AND(left, right) =>
      s"(${renderAndUnrolled(left)} AND ${renderAndUnrolled(right)})"
    case FilterExpression.NOT(expression) =>
      s"(NOT ${renderFilterExpression(expression)})"
  }

  def renderFilterDefinition(fd: FilterDefinition): String = fd match {
    case FilterDefinition.Term(fieldName, exp, tagOpt) =>
      val tag = tagOpt map (tag => s"{!tag=$tag}") getOrElse ""
      s"$tag$fieldName:${renderFilterExpression(exp)}"
    case FilterDefinition.OR(left, right) =>
      s"(${renderFilterDefinition(left)} OR ${renderFilterDefinition(right)})"
    case FilterDefinition.AND(left,right) =>
      s"(${renderFilterDefinition(left)} AND ${renderFilterDefinition(right)})"
  }

  implicit val sortingWriter = Writes[Sorting] {
    case Sorting(field, order) =>
      val ord = order match {
        case SortOrder.Asc => "asc"
        case SortOrder.Desc => "desc"
      }
      JsString(s"$field $ord")
  }

  implicit val filterDefinitionWriter = Writes[FilterDefinition] { fd =>
    JsString(renderFilterDefinition(fd))
  }

  implicit val facetMetadataDomainWriter: Writes[FacetMetadata.Domain] =
    (__ \ "excludeTags").writeNonEmptyList[String].contramap(_.excludeTags)

  implicit val termsMetadataWriter: Writes[FacetMetadata.Terms] = (
    (__ \ "type").write[String] and
      (__ \ "field").write[String] and
      (__ \ "limit").writeNullable[Long] and
      (__ \ "sort").writeNullable[Sorting] and
      (__ \ "facet").lazyWriteNonEmptyList(facetListWriter) and
      (__ \ "domain").writeNullable[FacetMetadata.Domain]
    )(m => ("terms", m.field, m.limit, m.sort, m.subFacets, m.domain))

  implicit val rangeIncludeWriter: Writes[FacetMetadata.Range.Include] = Writes( include =>
    JsString(include.toString.toLowerCase)
  )

  implicit val rangeMetadataWriter: Writes[FacetMetadata.Range] = (
    (__ \ "type").write[String] and
    (__ \ "field").write[String] and
    (__ \ "start").write[Long] and
    (__ \ "end").write[Long] and
    (__ \ "gap").write[Long] and
    (__ \ "sort").writeNullable[Sorting] and
    (__ \ "include").writeNonEmptyList[FacetMetadata.Range.Include] and
    (__ \ "facet").lazyWriteNonEmptyList(facetListWriter) and
    (__ \ "domain").writeNullable[FacetMetadata.Domain]
  )(m => ("range", m.field, m.start, m.end, m.gap, m.sort, m.include, m.subFacets, m.domain))

  implicit val uniqueMetadataWriter = Writes[FacetMetadata.Unique]{
    case FacetMetadata.Unique(field, function) =>
      JsString(s"$function($field)")
  }

  implicit val facetMetadataWriter = Writes[FacetMetadata] {
    case terms: FacetMetadata.Terms => Json toJson terms
    case range: FacetMetadata.Range => Json toJson range
    case unique: FacetMetadata.Unique => Json toJson unique
    case FacetMetadata.Min(field) => JsString(s"min($field)")
    case FacetMetadata.Max(field) => JsString(s"max($field)")
  }

  val facetListWriter = OWrites[List[FacetDefinition]] {
    case Nil => JsObject.empty
    case nonEmpty =>
      nonEmpty.foldLeft(JsObject.empty) {
        case (obj, FacetDefinition(name, metadata)) =>
          obj + (name -> facetMetadataWriter.writes(metadata))
      }
  }

  implicit val resultGroupingWriter: Writes[ResultGrouping] = (
    (__ \ "group").write[Boolean] and
    (__ \ "group.field").write[String] and
    (__ \ "group.sort").writeNullable[Sorting]
  )(rg => (true, rg.field, rg.sort))

  val routingListWriter = Writes[List[String]] { list =>
    JsString(list.map(_ + "!").mkString(","))
  }

  implicit val queryWriter: Writes[Query] = (
    (__ \ "query").write[String] and
    (__ \ "filter").writeNonEmptyList[FilterDefinition] and
    (__ \ "params" \ "_route_").writeNonEmptyList[String](routingListWriter) and
    (__ \ "params" \ "sort").writeNullable[Sorting] and
    (__ \ "params" \ "start").writeNullable[Long] and
    (__ \ "params" \ "rows").writeNullable[Long] and
    (__ \ "params").writeNullable[ResultGrouping] and
    (__ \ "facet").writeNonEmptyList(facetListWriter)
  )(q => ("*:*", q.filter, q.routing, q.sort, q.start, q.rows, q.grouping, q.facets))

  def toJson(query: Query) = queryWriter writes query
}

object DocumentListWriter {

  import JsonRenderCommon._

  implicit val fieldValueWriter = Writes[FieldValue] {
    def renderFieldValue(fieldValueValue: FieldValue): JsValue = fieldValueValue match {
      case FieldValue.Primitive(primitiveValue) => primitiveValue match {
        case FieldValue.PrimitiveValue.Date(value) => JsString(renderDate(value))
        case FieldValue.PrimitiveValue.Int(value) => JsString(value.toString)
        case FieldValue.PrimitiveValue.String(value) => JsString(value)
      }
      case FieldValue.Array(values) => JsArray(values map renderFieldValue)
      case FieldValue.Null => JsNull
    }
    renderFieldValue
  }

  implicit val documentWriter = Writes[Document] { document =>
    Json toJson document.fields
  }

  def toJson(documentList: DocumentList): JsValue = Json toJson documentList.documents
}

object ResponseReader {
  implicit val responseBodyReader = Json.reads[ResponseBody]

  implicit val responseFacetsReader = Reads.JsValueReads.map(ResponseFacets)

  implicit val queryResponseReader: Reads[QueryResponse] = (
    (__ \ "response").read[ResponseBody] and
    (__ \ "facets").readNullable[ResponseFacets]
  )(QueryResponse)
}
