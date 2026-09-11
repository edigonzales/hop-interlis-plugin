package ch.so.agi.hop.interlis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class VersionedFixtureLayoutTest {

  private static final String XTF_23 = "http://www.interlis.ch/INTERLIS2.3";
  private static final String XTF_24 = "http://www.interlis.ch/xtf/2.4/INTERLIS";

  @Test
  void model_sources_are_separated_by_interlis_version() throws Exception {
    assertThat(modelFiles("2.3"))
        .extracting(path -> Files.readString(path).lines().findFirst().orElseThrow())
        .allMatch(line -> line.equals("INTERLIS 2.3;"));
    assertThat(modelFiles("2.4"))
        .extracting(path -> Files.readString(path).lines().findFirst().orElseThrow())
        .allMatch(line -> line.equals("INTERLIS 2.4;"));
  }

  @Test
  void transfer_fixtures_are_separated_by_xtf_version() throws Exception {
    assertThat(dataFiles("2.3")).allSatisfy(path -> assertRootNamespace(path, XTF_23));
    assertThat(dataFiles("2.4")).allSatisfy(path -> assertRootNamespace(path, XTF_24));
  }

  private static List<Path> modelFiles(String version) throws Exception {
    try (var files = Files.list(TestResources.modelDirectory(version))) {
      return files.filter(path -> path.getFileName().toString().endsWith(".ili")).toList();
    }
  }

  private static List<Path> dataFiles(String version) throws Exception {
    try (var files = Files.list(TestResources.dataDirectory(version))) {
      return files.filter(path -> path.getFileName().toString().endsWith(".xtf")).toList();
    }
  }

  private static void assertRootNamespace(Path transfer, String namespace) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var root = factory.newDocumentBuilder().parse(transfer.toFile()).getDocumentElement();
      assertThat(root.getLocalName()).isEqualToIgnoringCase("transfer");
      assertThat(root.getNamespaceURI()).isEqualTo(namespace);
    } catch (Exception e) {
      throw new AssertionError("Could not parse XTF fixture " + transfer, e);
    }
  }
}
