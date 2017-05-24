package nl.elmar.solr.client

import nl.elmar.solr.request._

class QueryWriterSpec extends org.specs2.mutable.Specification {

  "QueryWriter" should {
    "render empty query" in {
      QueryWriter.toJson(Query()).toString === """{"query":"*:*"}"""
    }

    "render result grouping" in {
      val query =
        Query(
          start = Some(10),
          rows = Some(20),
          grouping = Some(
            ResultGrouping("field1", sort = Some(Sorting("field2", SortOrder.Desc)))
          )
        )
      QueryWriter.toJson(query).toString === """{"query":"*:*","params":{"start":10,"rows":20,"group":true,"group.field":"field1","group.sort":"field2 desc"}}"""
    }

    "render facets" in {
      val facets = FacetDefinition(
        name = "countries",
        metadata = FacetMetadata.Terms(
          field = "country",
          domain = Some(FacetMetadata.Domain("country" :: Nil)),
          subFacets = FacetDefinition(
            name = "uniqueCount",
            metadata = FacetMetadata.Unique("groupField", "unique")
          ) :: Nil
        )
      ) :: Nil
      val query = Query(facets = facets)
      QueryWriter.toJson(query).toString === """{"query":"*:*","facet":{"countries":{"type":"terms","field":"country","facet":{"uniqueCount":"unique(groupField)"},"domain":{"excludeTags":["country"]}}}}"""
    }

    "render AND expression" in {
      import FilterExpression._

      val exp = AND(Term.Long(1), AND(Term.Long(2), AND(Term.Long(3), Term.Long(4))))

      QueryWriter.renderFilterExpression(exp) === "(1 AND 2 AND 3 AND 4)"
    }

    "render AND and OR combination" in {
      import FilterExpression._

      val exp =
        OR(
          Term Long 0,
          OR(
            AND(Term Long 1, AND(Term Long 2, AND(Term Long 3, Term Long 4))),
            Term Long 10
          )
        )

      QueryWriter.renderFilterExpression(exp) === "(0 OR (1 AND 2 AND 3 AND 4) OR 10)"
    }
  }

}
