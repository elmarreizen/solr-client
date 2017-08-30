package nl.elmar.solr.request

import java.time.LocalDate

case class QueryRequest(
    collection: String,
    query: Query = Query()
)

case class Query(
    filter: List[FilterDefinition] = Nil,
    routing: List[String] = Nil,
    start: Option[Long] = None,
    rows: Option[Long] = None,
    sort: Option[Sorting] = None,
    grouping: Option[ResultGrouping] = None,
    facets: List[FacetDefinition] = Nil
)

case class FilterDefinition(fieldName: String, exp: FilterExpression, tag: Option[String] = None)

sealed trait FilterExpression

object FilterExpression {
  sealed trait Term extends FilterExpression

  object Term {
    case class Date(value: LocalDate) extends Term
    case class Long(value: scala.Long) extends Term
    case class String(value: java.lang.String) extends Term
  }

  case class OR(left: FilterExpression, right: FilterExpression) extends FilterExpression
  case class AND(left: FilterExpression, right: FilterExpression) extends FilterExpression
  case class NOT(FilterExpression: FilterExpression) extends FilterExpression

  case class Range(from: Option[Term], to: Option[Term]) extends FilterExpression
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
    sort: Option[Sorting]
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
