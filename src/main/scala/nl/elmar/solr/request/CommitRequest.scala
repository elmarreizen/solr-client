package nl.elmar.solr.request

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class CommitRequest(

)

case class CommitResponse(status: Int, queryTime: Long)

object CommitResponse {
  implicit val reader = (
    (__ \ "responseHeader" \ "status").read[Int] and
    (__ \ "responseHeader" \ "QTime").read[Long]
  )(CommitResponse.apply _)
}
