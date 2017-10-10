package nl.elmar.solr.client

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestKitBase
import nl.elmar.solr.client.api.SolrApi
import org.apache.commons.io.FileUtils
import org.apache.solr.client.solrj.embedded.JettySolrRunner
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.BeforeAfterAll

abstract class SolrSpec
  extends { implicit val system = ActorSystem("test-system") }
    with TestKitBase
    with SpecificationLike
    with BeforeAfterAll {

  private val tempDir = Files.createTempDirectory("solr-data-")
  private var runner: JettySolrRunner = _

  implicit val materializer = ActorMaterializer()

  def solrApi: SolrApi = new SolrApi(Uri("http://localhost:8983"))

  override def beforeAll() = {
    System.setProperty("solr.data.dir", tempDir.toFile.getAbsolutePath)
    runner = new JettySolrRunner("./src/test/resources/solr", "/solr", 8983)
    runner.start(true)
    Thread.sleep(3000)
  }

  override def afterAll() = {
    runner.stop()
    FileUtils.deleteDirectory(tempDir.toFile)
  }
}
