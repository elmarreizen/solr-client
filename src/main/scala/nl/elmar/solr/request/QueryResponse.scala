package nl.elmar.solr.request

import nl.elmar.solr._
import play.api.libs.json._

case class QueryResponse(
    body: ResponseBody,
    facets: Option[ResponseFacets]
)

case class ResponseBody(
    numFound: Long,
    start: Long,
    docs: JsValue
)

case class ResponseFacets(raw: JsValue) {
  def count = (raw \ "count").as[Long]
}

class QueryResponseParser(response: QueryResponse, schema: Schema) {

  private val localDateReader = Reads.localDateReads("yyyy-MM-dd'T00:00:00Z'")

  private val documentReader = Reads[Document] { jsValue =>

    def typeReader(dataType: DataType): Reads[FieldValue] = {
      import FieldValue._
      dataType match {
        case DataType.Date => localDateReader.map(date => Primitive(PrimitiveValue.Date(date)))
        case DataType.Int => implicitly[Reads[Int]].map(int => Primitive(PrimitiveValue.Int(int)))
        case DataType.String => implicitly[Reads[String]].map(string => Primitive(PrimitiveValue.String(string)))
        case DataType.Array(ofType) => Reads.list(typeReader(ofType)).map( list => FieldValue Array list)
        case DataType.Optional(ofType) => Reads.optionWithNull(typeReader(ofType)).map( opt => opt getOrElse FieldValue.Null)
      }
    }

    def readObject(jsObject: JsObject): JsResult[List[(String, FieldValue)]] = {
      schema.fields.foldLeft(JsSuccess(Nil): JsResult[List[(String, FieldValue)]]){
        case (result, (name, dataType)) =>
          for {
            acc <- result
            value <- (jsObject \ name).validate(typeReader(dataType))
          }
          yield
            (name, value) :: acc
      }
    }

    jsValue.validate[JsObject].flatMap(readObject).map { fields =>
      Document(fields.toMap)
    }
  }


  def docs: JsResult[DocumentList] = {
    response.body.docs.validate(Reads list documentReader).map(DocumentList(_))
  }
}
