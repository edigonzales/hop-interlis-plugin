package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  private static final int UNREACHABLE_PORT = findFreePort();

  @TempDir Path cacheDir;

  @BeforeAll
  static void startRepository() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          Path resource = Path.of("/models/repo").resolve(path.substring(1));
          java.net.URL url = InterlisModelRepositoryTest.class.getResource(resource.toString());
          if (url == null) {
            try {
              exchange.sendResponseHeaders(404, -1);
            } catch (java.io.IOException e) {
              throw new RuntimeException(e);
            }
            return;
          }
          try {
            byte[] content = Files.readAllBytes(Path.of(url.toURI()));
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          exchange.close();
        });
    server.start();
    repositoryUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
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
}
