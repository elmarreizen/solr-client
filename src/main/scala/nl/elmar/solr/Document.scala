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
  case class Primitive(value: PrimitiveValue) extends FieldValue
  trait PrimitiveValue
  object PrimitiveValue {
    case class Date(value: LocalDate) extends PrimitiveValue
    case class Int(value: scala.Int) extends PrimitiveValue
    case class String(value: java.lang.String) extends PrimitiveValue
  }
  case class Array(value: List[FieldValue]) extends FieldValue
  case object Null extends FieldValue
}

