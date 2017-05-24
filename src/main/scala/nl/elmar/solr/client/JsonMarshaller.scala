package nl.elmar.solr.client

import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaTypes.`application/json`
import play.api.libs.json.{Json, Reads, Writes}

object JsonMarshaller {

  implicit def marshaller[A: Writes] = (
    Marshaller.stringMarshaller(`application/json`)
      compose Json.stringify
      compose implicitly[Writes[A]].writes
  )


  implicit def unmarshaller[A: Reads]: Unmarshaller[HttpEntity, A] =
    Unmarshaller.stringUnmarshaller.map { s =>
      Json.parse(s).as[A]
    }

}
