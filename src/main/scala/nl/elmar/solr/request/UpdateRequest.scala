package nl.elmar.solr.request

import nl.elmar.solr._

case class UpdateRequest(
    collection: String,
    payload: DocumentList
)

