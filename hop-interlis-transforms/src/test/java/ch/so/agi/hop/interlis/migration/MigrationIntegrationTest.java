package ch.so.agi.hop.interlis.migration;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MigrationIntegrationTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundtripRetainsSettingsAndActionContract() throws Exception {
    var action = new InterlisMigration();
    action.setMappingFile("${MAP}");
    action.setOutputFile("${OUTPUT}");
    action.setModelDirectories("models;other");
    action.setValidate(false);
    action.setOverwrite(true);
    action.setReportDirectory("reports");
    String xml = "<action>" + action.getXml() + "</action>";
    var reloaded = new InterlisMigration();
    reloaded.loadXml(
        XmlHandler.loadXmlString(xml).getDocumentElement(),
        new MemoryMetadataProvider(),
        new Variables());
    assertThat(reloaded.getMappingFile()).isEqualTo("${MAP}");
    assertThat(reloaded.getOutputFile()).isEqualTo("${OUTPUT}");
    assertThat(reloaded.isValidate()).isFalse();
    assertThat(reloaded.isOverwrite()).isTrue();
    assertThat(reloaded.isEvaluation()).isTrue();
    assertThat(reloaded.isUnconditional()).isFalse();
    assertThat(reloaded.getDialogClassName()).isEqualTo(InterlisMigrationDialog.class.getName());
    var annotation = InterlisMigration.class.getAnnotation(Action.class);
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(InterlisMigration.class.getResource("/" + annotation.image())).isNotNull();
  }

  @Test
  void defaultsAndMissingVariablesFailBeforeCreatingOutput() {
    var action = new InterlisMigration();
    assertThat(action.isValidate()).isTrue();
    assertThat(action.isOverwrite()).isFalse();
    action.setMappingFile("${MISSING_MIGRATION_MAPPING}");
    assertThatThrownBy(action::request).hasMessageContaining("Unresolved variable");
    Result result = action.execute(new Result(), 0);
    assertThat(result.getResult()).isFalse();
    assertThat(result.getNrErrors()).isEqualTo(1);
  }

  @Test
  void localVfsUrisAndBracketMatching() {
    Path mapping = dir.resolve("mapping with spaces.ilimap");
    var action = new InterlisMigration();
    action.setMappingFile(mapping.toUri().toString());
    assertThat(action.request().mapping()).isEqualTo(mapping);
    assertThatThrownBy(() -> MigrationPaths.local("s3://bucket/mapping.ilimap"))
        .hasMessageContaining("local file");
    String expression = "concat(\"(\", trim(s.Name)) // (";
    assertThat(IlimapSourceEditor.matchingBrackets(expression, 7)).containsExactly(6, 24);
    assertThat(IlimapSourceEditor.matchingBrackets(expression, 9)).isEmpty();
    assertThat(IlimapSourceEditor.matchingBrackets("mapping v2 {", 12)).isEmpty();
  }

  @Test
  void saveDetectsExternalChangesAndPreservesExactUtf8Source() throws Exception {
    Path path = dir.resolve("mapping.ilimap");
    String text = "// Prüfung\r\nmapping v2 {}\n";
    Files.writeString(path, text);
    var file = new MappingFile(path);
    assertThat(file.text()).isEqualTo(text);
    file.save(text + "// comment\n");
    assertThat(Files.readString(path)).isEqualTo(text + "// comment\n");
    Files.writeString(path, "external");
    assertThatThrownBy(() -> file.save("replacement")).hasMessageContaining("outside");
    assertThat(Files.readString(path)).isEqualTo("external");
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).containsExactly(path);
    }
  }
}
