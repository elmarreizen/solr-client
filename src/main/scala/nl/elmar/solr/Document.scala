package nl.elmar.solr

import java.time.LocalDate

case class Document(fields: Map[String, FieldValue])
object Document {
  def apply(fields: (String, FieldValue)*) = new Document(fields.toMap)
}

case class DocumentList(documents: List[Document])
object DocumentList {
  def apply(docs: Document*) = new DocumentList(docs.toList)
}

sealed trait FieldValue

object FieldValue {
  sealed trait Primitive extends FieldValue
  object Primitive {
    case class Date(value: LocalDate) extends Primitive
    case class Long(value: scala.Long) extends Primitive
    case class String(value: java.lang.String) extends Primitive
  }
  case class Array(value: List[FieldValue]) extends FieldValue
  case object Null extends FieldValue
}

