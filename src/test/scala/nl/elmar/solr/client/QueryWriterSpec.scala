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

    "render AND and OR expression combination" in {
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

    "render filter definition" in {
      val fd1 = FilterDefinition("test1", FilterExpression.Term.Long(0), Some("testtag1"))

      QueryWriter.renderFilterDefinition(fd1) === "{!tag=testtag1}test1:0"
    }

    "render OR filter definition" in {
      import FilterDefinition._

      val fd1 = FilterDefinition("test1", FilterExpression.Term.Long(0), Some("testtag1"))
      val fd2 = FilterDefinition("test2", FilterExpression.Term.String("testexp"), Some("testtag2"))
      val or = fd1 or fd2

      QueryWriter.renderFilterDefinition(or) === "({!tag=testtag1}test1:0 OR {!tag=testtag2}test2:testexp)"
    }

    "render AND filter definition" in {
      import FilterDefinition._

      val fd1 = FilterDefinition("test1", FilterExpression.Term.Long(0), Some("testtag1"))
      val fd2 = FilterDefinition("test2", FilterExpression.Term.String("testexp"), Some("testtag2"))
      val and = fd1 and fd2

      QueryWriter.renderFilterDefinition(and) === "({!tag=testtag1}test1:0 AND {!tag=testtag2}test2:testexp)"
    }
  }

}
