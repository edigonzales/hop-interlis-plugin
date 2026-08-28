package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Model repository resilience: models resolved from http(s) INTERLIS model repositories through
 * the ilirepository machinery, served by a local HTTP server so the tests stay offline.
 */
class InterlisModelRepositoryTest {

  private static HttpServer server;
  private static String repositoryUri;
  private static String siteRootRepositoryUri;
  private static final int UNREACHABLE_PORT = findFreePort();

  @TempDir Path cacheDir;

  @BeforeAll
  static void startRepository() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          try {
            byte[] content;
            if ("/site-root/ilisite.xml".equals(path)) {
              content = siteMetadata().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if ("/site-root/ilimodels.xml".equals(path)) {
              content = emptyModelIndex().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
              String resourcePath = path.startsWith("/site-child/")
                  ? "/models/repo/" + path.substring("/site-child/".length())
                  : "/models/repo/" + path.substring(1);
              java.net.URL url =
                  InterlisModelRepositoryTest.class.getResource(resourcePath);
              if (url == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
              }
              content = Files.readAllBytes(Path.of(url.toURI()));
            }
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    server.start();
    repositoryUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    siteRootRepositoryUri =
        "http://127.0.0.1:" + server.getAddress().getPort() + "/site-root/";
  }

  @AfterAll
  static void stopRepository() {
    if (server != null) {
      server.stop(0);
    }
  }

  private InterlisModelServiceImpl service() {
    System.setProperty("hop.interlis.repository.cache", cacheDir.toString());
    return new InterlisModelServiceImpl();
  }

  @Test
  void resolves_models_from_an_http_repository_including_imports() throws Exception {
    CompiledInterlisModel model =
        service()
            .compile(
                new ModelSource(
                    List.of(), List.of("HopIli_RepoMain_V1"), List.of(repositoryUri)),
                ModelCompileOptions.defaults());

    assertThat(model.compiledModelNames()).contains("HopIli_RepoMain_V1");
    assertThat(model.transferDescription().getElement("HopIli_RepoBase_V1.BaseTopic.Base"))
        .isNotNull();
  }

  @Test
  void model_only_projection_probe_lists_classes_from_local_repository_fixture() throws Exception {
    var context =
        new InterlisProjectionService()
            .loadModel(
                new InterlisModelRequest(
                    null, List.of("HopIli_RepoMain_V1"), List.of(repositoryUri)));

    assertThat(context.modelNames()).contains("HopIli_RepoMain_V1");
    assertThat(context.schema().classes())
        .extracting(InterlisClassDescriptor::scopedName)
        .contains("HopIli_RepoMain_V1.MainTopic.Main");
  }

  @Test
  void resolves_models_from_a_subsidiary_repository_via_ilisite() throws Exception {
    CompiledInterlisModel model =
        service()
            .compile(
                new ModelSource(
                    List.of(), List.of("HopIli_RepoMain_V1"), List.of(siteRootRepositoryUri)),
                ModelCompileOptions.defaults());

    assertThat(model.compiledModelNames()).contains("HopIli_RepoMain_V1");
    assertThat(model.transferDescription().getElement("HopIli_RepoMain_V1.MainTopic.Main"))
        .isNotNull();
  }

  @Test
  void local_directories_override_the_repository() throws Exception {
    Path localDir = Path.of(InterlisModelRepositoryTest.class.getResource("/models").toURI());
    String localBase = localDir.resolve("HopIli_Associations_V1.ili").getParent().toString();

    CompiledInterlisModel model =
        service()
            .compile(
                new ModelSource(
                    List.of(),
                    List.of("HopIli_Associations_V1"),
                    List.of(localBase, "http://127.0.0.1:" + UNREACHABLE_PORT + "/")),
                ModelCompileOptions.defaults());

    assertThat(model.compiledModelNames()).contains("HopIli_Associations_V1");
  }

  @Test
  void unreachable_repository_fails_with_actionable_diagnostics() {
    InterlisModelServiceImpl service = service();

    assertThatThrownBy(
            () ->
                service.compile(
                    new ModelSource(
                        List.of(),
                        List.of("HopIli_RepoMain_V1"),
                        List.of("http://127.0.0.1:" + UNREACHABLE_PORT + "/")),
                    ModelCompileOptions.defaults()))
        .isInstanceOf(InterlisModelException.class)
        .hasMessageContaining("unreachable")
        .hasMessageContaining("HopIli_RepoMain_V1");
  }

  @Test
  void unknown_model_in_repository_is_reported_clearly() {
    InterlisModelServiceImpl service = service();

    assertThatThrownBy(
            () ->
                service.compile(
                    new ModelSource(
                        List.of(), List.of("HopIli_NoSuchModel_V1"), List.of(repositoryUri)),
                    ModelCompileOptions.defaults()))
        .isInstanceOf(InterlisModelException.class)
        .hasMessageContaining("does not contain model HopIli_NoSuchModel_V1");
  }

  private static int findFreePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception e) {
      return 9; // discard port; connection refused
    }
  }

  private static String siteMetadata() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
          <HEADERSECTION SENDER="hop-interlis-tests" VERSION="2.3">
            <MODELS><MODEL NAME="IliSite09" VERSION="2009-11-12" URI="mailto:tests@example.ch"/></MODELS>
          </HEADERSECTION>
          <DATASECTION>
            <IliSite09.SiteMetadata BID="b0">
              <IliSite09.SiteMetadata.Site TID="1">
                <Name>test-root</Name>
                <subsidiarySite>
                  <IliSite09.RepositoryLocation_>
                    <value>http://127.0.0.1:%d/site-child</value>
                  </IliSite09.RepositoryLocation_>
                </subsidiarySite>
              </IliSite09.SiteMetadata.Site>
            </IliSite09.SiteMetadata>
          </DATASECTION>
        </TRANSFER>
        """.formatted(server.getAddress().getPort());
  }

  private static String emptyModelIndex() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
          <HEADERSECTION SENDER="hop-interlis-tests" VERSION="2.3">
            <MODELS><MODEL NAME="IliRepository20" VERSION="2020-01-15" URI="http://models.interlis.ch/core"/></MODELS>
          </HEADERSECTION>
          <DATASECTION>
            <IliRepository20.RepositoryIndex BID="b0">
              <IliRepository20.RepositoryIndex.ModelMetadata TID="0">
                <Name>HopIli_Unrelated_V1</Name>
                <SchemaLanguage>ili2_4</SchemaLanguage>
                <File>HopIli_Unrelated_V1.ili</File>
                <Version>2026-01-01</Version>
                <publishingDate>2026-01-01</publishingDate>
                <Issuer>mailto:tests@example.ch</Issuer>
              </IliRepository20.RepositoryIndex.ModelMetadata>
            </IliRepository20.RepositoryIndex>
          </DATASECTION>
        </TRANSFER>
        """;
  }
}
