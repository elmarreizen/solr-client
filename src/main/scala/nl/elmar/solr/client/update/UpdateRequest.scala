package nl.elmar.solr.client.update

import nl.elmar.solr._
import play.api.libs.json._

sealed trait UpdateRequest

case class BatchUpdateRequest(
    documents: List[Document]
) extends UpdateRequest

object UpdateRequest {
  import nl.elmar.solr.client.CommonWriters._

  implicit val fieldValueWriter: Writes[FieldValue] = {
    case FieldValue.Primitive.Date(value) => JsString(renderDate(value))
    case FieldValue.Primitive.Long(value) => JsNumber(value)
    case FieldValue.Primitive.String(value) => JsString(value)
    case FieldValue.Array(values) => JsArray(values map fieldValueWriter.writes)
    case FieldValue.Null => JsNull
  }

  implicit val documentWriter: Writes[Document] = {
    document =>
      Json toJson document.fields
  }

  implicit val documentListWriter: Writes[List[Document]] = Writes.list[Document]
}
