package nl.elmar.solr

sealed trait DataType

object DataType {
  case object Date extends DataType
  case object Int extends DataType
  case object String extends DataType
  case class Array(ofType: DataType) extends DataType
  case class Optional(ofType: DataType) extends DataType
}
