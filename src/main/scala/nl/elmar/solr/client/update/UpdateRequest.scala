package nl.elmar.solr.client.update

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, Uri}
import nl.elmar.solr._
import play.api.libs.json._

case class UpdateRequest(
    collection: String,
    payload: DocumentList
)

object UpdateRequest {
  import nl.elmar.solr.client.CommonWriters._

  def toRequest(solrUri: Uri, request: UpdateRequest): HttpRequest = {
    HttpRequest(
      uri = solrUri withPath solrUri.path / request.collection / "update",
      entity =
        HttpEntity(
          ContentTypes.`application/json`,
          documentListWriter.writes(request.payload).toString
        )
    )
  }

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

  implicit val documentListWriter: Writes[DocumentList] = {
    documentList =>
      Json toJson documentList.documents
  }
}
