package nl.elmar.solr.client.api

import akka.http.scaladsl.model.HttpRequest

trait Endpoint[Input] {
  def toHttpRequest(input: Input): HttpRequest
}
