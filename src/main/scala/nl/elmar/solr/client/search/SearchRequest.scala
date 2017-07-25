package nl.elmar.solr.client.search

import akka.http.scaladsl.model._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class SearchRequest(
    collection: String,
    body: SearchRequestBody = SearchRequestBody()
)

object SearchRequest {
  def toHttpRequest(solrUri: Uri, request: SearchRequest): HttpRequest = {
    HttpRequest(
      uri = solrUri withPath solrUri.path / request.collection / "query",
      entity =
        HttpEntity(
          ContentTypes.`application/json`,
          bodyWriter.writes(request.body).toString
        )
    )
  }

  import nl.elmar.solr.client.CommonWriters._

  def renderOrUnrolled(exp: ValueExpression): String = {
    exp match {
      case ValueExpression.OR(left: ValueExpression, right: ValueExpression) =>
        s"${renderOrUnrolled(left)} OR ${renderOrUnrolled(right)}"
      case other: ValueExpression =>
        renderValueExpression(other)
    }
  }

  def renderAndUnrolled(exp: ValueExpression): String = {
    exp match {
      case ValueExpression.AND(left: ValueExpression, right: ValueExpression) =>
        s"${renderAndUnrolled(left)} AND ${renderAndUnrolled(right)}"
      case other: ValueExpression =>
        renderValueExpression(other)
    }
  }

  def renderValueExpression(value: ValueExpression): String = value match {
    case ValueExpression.Term.String(v) => if (OnlyLetterDigit.findAllIn(v).hasNext) v else raw""""$v""""
    case ValueExpression.Term.Long(v) => v.toString
    case ValueExpression.Term.Date(v) => renderDate(v)
    case ValueExpression.Range(fromOpt, toOpt) =>
      val from = fromOpt.map(renderValueExpression).getOrElse("*")
      val to = toOpt.map(renderValueExpression).getOrElse("*")
      s"[$from TO $to]"
    case ValueExpression.OR(left, right) =>
      s"(${renderOrUnrolled(left)} OR ${renderOrUnrolled(right)})"
    case ValueExpression.AND(left, right) =>
      s"(${renderAndUnrolled(left)} AND ${renderAndUnrolled(right)})"
    case ValueExpression.NOT(expression) =>
      s"(NOT ${renderValueExpression(expression)})"
  }

  def renderFilterExpression(fd: FilterExpression): String = fd match {
    case FilterExpression.Field(fieldName, exp, tagOpt) =>
      val tag = tagOpt map (tag => s"{!tag=$tag} ") getOrElse ""
      s"$tag$fieldName:${renderValueExpression(exp)}"
    case FilterExpression.OR(left, right) =>
      s"${renderFilterExpression(left)} OR ${renderFilterExpression(right)}"
    case ve: ValueExpression =>
      renderValueExpression(ve)
  }

  implicit val sortingWriter = Writes[Sorting] {
    case Sorting(field, order) =>
      val ord = order match {
        case SortOrder.Asc => "asc"
        case SortOrder.Desc => "desc"
      }
      JsString(s"$field $ord")
  }

  implicit val FilterExpressionWriter = Writes[FilterExpression] { fd =>
    JsString(renderFilterExpression(fd))
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

  val routingListWriter: Writes[List[String]] = { list =>
    JsString(list.map(_ + "!").mkString(","))
  }

  implicit val bodyWriter: Writes[SearchRequestBody] = (
    (__ \ "query").write[String] and
      (__ \ "filter").writeNonEmptyList[FilterExpression] and
      (__ \ "params" \ "_route_").writeNonEmptyList[String](routingListWriter) and
      (__ \ "params" \ "sort").writeNullable[Sorting] and
      (__ \ "params" \ "start").writeNullable[Long] and
      (__ \ "params" \ "rows").writeNullable[Long] and
      (__ \ "params").writeNullable[ResultGrouping] and
      (__ \ "facet").writeNonEmptyList(facetListWriter)
    )(q => ("*:*", q.filter, q.routing, q.sort, q.start, q.rows, q.grouping, q.facets))
}

