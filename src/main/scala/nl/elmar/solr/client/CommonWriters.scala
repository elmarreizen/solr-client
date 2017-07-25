package nl.elmar.solr.client

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json.{JsPath, OWrites, Writes}
import play.api.libs.functional.syntax._

object CommonWriters {
  val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T00:00:00Z'")

  val OnlyLetterDigit = "^[a-zA-Z0-9]+$".r

  def renderDate(date: LocalDate) = {
    val rendered = date format dateTime
    raw""" "$rendered" """
  }

  implicit class JsPathOps(val jsPath: JsPath) extends AnyVal {
    def writeNonEmptyList[A](implicit AListWriter: Writes[List[A]]) =
      jsPath
        .writeNullable(AListWriter)
        .contramap[List[A]] {
        case Nil => None
        case nonEmpty => Some(nonEmpty)
      }

    def lazyWriteNonEmptyList[A](AListWriter: => Writes[List[A]]) = {
      lazy val writer = writeNonEmptyList(AListWriter)
      OWrites[List[A]](list => writer writes list)
    }
  }

}
