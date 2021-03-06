package nl.elmar.solr.client.search

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.LocalDate

case class SearchRequest(
    filter: List[FilterExpression] = Nil,
    routing: List[String] = Nil,
    start: Option[Long] = None,
    rows: Option[Long] = None,
    sort: Option[Sorting] = None,
    grouping: Option[ResultGrouping] = None,
    fieldCollapsing: Option[FieldCollapsing] = None,
    facets: List[FacetDefinition] = Nil
)

sealed trait FilterExpression

object FilterExpression {

  case class Field(
      name: String,
      exp: ValueExpression,
      tag: Option[String] = None,
      negative: Boolean = false
  ) extends FilterExpression

  case class OR(left: FilterExpression, right: FilterExpression) extends FilterExpression
  case class AND(left: FilterExpression, right: FilterExpression) extends FilterExpression
  case class NOT(expr: FilterExpression) extends FilterExpression
}

sealed trait ValueExpression extends FilterExpression

object ValueExpression {
  sealed trait Term extends ValueExpression

  object Term {
    case object AnyValue extends Term
    case class Date(value: LocalDate) extends Term
    case class Long(value: scala.Long) extends Term
    case class String(value: java.lang.String) extends Term
  }

  object Wildcard {
    case class PrefixMatch(prefix: String) extends ValueExpression
  }

  case class OR(left: ValueExpression, right: ValueExpression) extends ValueExpression
  case class AND(left: ValueExpression, right: ValueExpression) extends ValueExpression
  case class NOT(exp: ValueExpression) extends ValueExpression

  case class Range(from: Option[Term], to: Option[Term]) extends ValueExpression
}

case class FacetDefinition(
    name: String,
    metadata: FacetMetadata
)

sealed trait FacetMetadata

object FacetMetadata {
  sealed trait FormBuckets

  case class Terms(
      field: String,
      sort: Option[Sorting] = None,
      limit: Option[Long] = None,
      subFacets: List[FacetDefinition] = Nil,
      domain: Option[Domain] = None
  ) extends FacetMetadata with FormBuckets

  case class Range(
      field: String,
      start: Long,
      gap: Long,
      end: Long,
      sort: Option[Sorting] = None,
      include: List[Range.Include] = Nil,
      subFacets: List[FacetDefinition] = Nil,
      domain: Option[Domain] = None
  ) extends FacetMetadata with FormBuckets

  object Range {
    object Include extends Enumeration {
      val Lower, Upper, Edge, Outer, All = Value
    }
    type Include = Include.Value
  }

  case class Unique(
      field: String,
      function: String = "hll"
  ) extends FacetMetadata

  case class Min(field: String) extends FacetMetadata

  case class Max(field: String) extends FacetMetadata

  case class Domain(excludeTags: List[String])

  implicit class BucketFacetOps[A <: FormBuckets](val thisFacet: FormBuckets) extends AnyVal {
    def addSubFacet(facet: FacetDefinition) = thisFacet match {
      case terms: Terms => terms.copy(subFacets = facet :: terms.subFacets)
      case range: Range => range.copy(subFacets = facet :: range.subFacets)
    }

    def withDomain(domain: Option[Domain]) = thisFacet match {
      case terms: Terms => terms.copy(domain = domain)
      case range: Range => range.copy(domain = domain)
    }

    def withDomain(domain: Domain): FacetMetadata = withDomain(Some(domain))
  }
}

case class ResultGrouping(
    field: String,
    sort: Option[Sorting],
    numberOfGroups: Boolean = false
)

case class FieldCollapsing(
    field: String,
    sorting: Sorting
)

case class Sorting(
    field: String,
    order: SortOrder
)

sealed trait SortOrder

object SortOrder {
  case object Asc extends SortOrder
  case object Desc extends SortOrder
}

object SearchRequest {

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
    case ValueExpression.Term.AnyValue => "*"
    case ValueExpression.Term.String(v) => if (OnlyLetterDigit.findAllIn(v).hasNext) v else raw""""$v""""
    case ValueExpression.Term.Long(v) => v.toString
    case ValueExpression.Term.Date(v) => renderDate(v)
    case ValueExpression.Wildcard.PrefixMatch(v) => s"$v*"
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

  def renderFilterExpression(fd: FilterExpression): String = {
    def loop(fd: FilterExpression): (String, List[String])= {
      fd match {
        case FilterExpression.Field(fieldName, exp, tagOpt, negative) =>
          val negation = if (negative) "-" else ""
          (s"$negation$fieldName:${renderValueExpression(exp)}", tagOpt.toList)

        case FilterExpression.OR(leftFilter, rightFilter) =>
          val (leftRendered, leftTags) = loop(leftFilter)
          val (rightRendered, rightTags) = loop(rightFilter)
          (s"($leftRendered OR $rightRendered)",  leftTags ::: rightTags)

        case FilterExpression.AND(leftFilter, rightFilter) =>
          val (leftRendered, leftTags) = loop(leftFilter)
          val (rightRendered, rightTags) = loop(rightFilter)
          (s"($leftRendered AND $rightRendered)", leftTags ::: rightTags)

        case FilterExpression.NOT(expr) =>
          val (rendered, exprTags) = loop(expr)
          (s"!($rendered)", exprTags)

        case ve: ValueExpression =>
          (renderValueExpression(ve), Nil)
      }
    }

    val (renderedFilter, tags) = loop(fd)
    val renderedTags = if (tags.isEmpty) "" else tags.toSet.mkString("{!tag=", ",", "}")

    s"$renderedTags$renderedFilter"
  }

  def soringAsString(sorting: Sorting): String = sorting match {
    case Sorting(field, order) =>
      val ord = order match {
        case SortOrder.Asc => "asc"
        case SortOrder.Desc => "desc"
      }
      s"$field $ord"
  }

  implicit val sortingWriter = Writes[Sorting] {
    sorting =>
      JsString(soringAsString(sorting))
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
      (__ \ "group.sort").writeNullable[Sorting] and
      (__ \ "group.ngroups").write[Boolean]
    )(rg => (true, rg.field, rg.sort, rg.numberOfGroups))

  val routingListWriter: Writes[List[String]] = { list =>
    JsString(list.map(_ + "!").mkString(","))
  }

  implicit val fieldCollapsingWriter: Writes[FieldCollapsing] = {
    case FieldCollapsing(field, sorting) =>
      val sortingString = soringAsString(sorting)
      JsString(s"{!collapse field=$field sort='$sortingString'}")
  }

  implicit val bodyWriter: Writes[SearchRequest] = (
    (__ \ "query").write[String] and
      (__ \ "filter").writeNonEmptyList[FilterExpression] and
      (__ \ "params" \ "_route_").writeNonEmptyList[String](routingListWriter) and
      (__ \ "params" \ "fq").writeNullable[FieldCollapsing] and
      (__ \ "params" \ "sort").writeNullable[Sorting] and
      (__ \ "params" \ "start").writeNullable[Long] and
      (__ \ "params" \ "rows").writeNullable[Long] and
      (__ \ "params").writeNullable[ResultGrouping] and
      (__ \ "facet").writeNonEmptyList(facetListWriter)
    )(q => ("*:*", q.filter, q.routing, q.fieldCollapsing, q.sort, q.start, q.rows, q.grouping, q.facets))
}

