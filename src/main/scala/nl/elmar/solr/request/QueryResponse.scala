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
      dataType match {
        case DataType.Date => localDateReader.map(date => FieldValue.Primitive Date date)
        case DataType.Int => implicitly[Reads[Int]].map(int => FieldValue.Primitive Int int)
        case DataType.String => implicitly[Reads[String]].map(string => FieldValue.Primitive String string)
        case DataType.Array(ofType) => Reads.list(typeReader(ofType)).map( list => FieldValue Array list)
        case DataType.Optional(ofType) => Reads.optionWithNull(typeReader(ofType)).map( opt => opt getOrElse FieldValue.Primitive.Null)
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
