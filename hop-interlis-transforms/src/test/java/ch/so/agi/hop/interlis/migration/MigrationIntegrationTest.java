package ch.so.agi.hop.interlis.migration;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;
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

  @Test
  void previewShowsBasketAndExplicitReferenceBidForAssociationWithoutTid() throws Exception {
    Path fixtures = Path.of("../hop-interlis-core/src/test/resources/fixtures");
    Path input = fixtures.resolve("data/2.4/HopIli_Associations_V1_extref.xtf");
    var model =
        new InterlisModelServiceImpl()
            .compile(
                new ModelSource(
                    List.of(fixtures.resolve("models/2.4/HopIli_Associations_V1.ili")),
                    List.of(),
                    List.of()),
                ModelCompileOptions.defaults())
            .transferDescription();

    assertThat(MigrationPreview.compare(input, model, input, model))
        .contains(
            "HopIli_Associations_V1.Data.PersonTask BID=b1\n",
            "Task[0] = REF t2 BID=b2\n",
            "Task[0] = REF t1\n")
        .doesNotContain("TID=null");
  }

  @Test
  void previewFailsForMappingAndValidationErrors() throws Exception {
    Path example = Path.of("../examples/migration").toAbsolutePath();
    Path mapping = dir.resolve("migration.ilimap");
    Files.copy(example.resolve("MigrationDemo.ili"), dir.resolve("MigrationDemo.ili"));
    String text = Files.readString(example.resolve("migration.ilimap"));
    Path source = example.resolve("source.xtf");

    Files.writeString(mapping, text.replace("s.Label", "s.Missing"));
    assertThatThrownBy(() -> MigrationPreview.run(mapping, source)).hasMessageContaining("s.Missing");

    Files.writeString(
        mapping,
        text.replace("migrated", "destination")
            .replace("\"migration\"", "\"" + "x".repeat(100) + "\""));
    var failure = catchThrowableOfType(IOException.class, () -> MigrationPreview.run(mapping, source));
    assertThat(failure)
        .hasMessageContaining("Validator reported errors for output destination")
        .hasMessageContaining("Attribute Origin is length restricted to 30");
    var transfer = Pattern.compile("dataFile <([^>]+)>").matcher(failure.getMessage());
    assertThat(transfer.find()).isTrue();
    assertThat(Path.of(transfer.group(1)).getParent()).doesNotExist();
  }

  @Test
  void successfulPreviewShowsOnlyWarningsProducedByMigration() throws Exception {
    Path example = Path.of("../examples/migration").toAbsolutePath();
    Path mapping = Files.copy(example.resolve("migration.ilimap"), dir.resolve("migration.ilimap"));
    Files.copy(example.resolve("MigrationDemo.ili"), dir.resolve("MigrationDemo.ili"));
    Path source = example.resolve("source.xtf");
    assertThat(MigrationPreview.run(mapping, source)).doesNotContain("\nWARNINGS\n");

    Path unresolved = dir.resolve("unresolved.xtf");
    Files.writeString(
        unresolved, Files.readString(source).replace("ili:ref=\"f1\"", "ili:ref=\"missing\""));
    assertThat(MigrationPreview.run(mapping, unresolved))
        .contains(
            "Complete preview file validated.",
            "\nWARNINGS\nILITRF-RUN-REF-UNRESOLVED: Optional reference unresolved for ");
  }

  @Test
  void previewIncludesLossReportOnlyForActualLosses() throws Exception {
    Path example = Path.of("../examples/migration").toAbsolutePath();
    Path mapping = dir.resolve("migration.ilimap");
    Files.copy(example.resolve("MigrationDemo.ili"), dir.resolve("MigrationDemo.ili"));
    String text = Files.readString(example.resolve("migration.ilimap"));
    Path source = example.resolve("source.xtf");

    Files.writeString(mapping, text);
    assertThat(MigrationPreview.run(mapping, source)).doesNotContain("INFORMATION LOSS");

    Files.writeString(
        mapping,
        text.substring(0, text.lastIndexOf("  rule "))
            + """
                rule discardLines {
                  source s from original class "MigrationDemo_V1.Data.Line";
                  loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Lines retired"; }
                }
              }
              """);
    assertThat(MigrationPreview.run(mapping, source))
        .contains(
            "INFORMATION LOSS",
            "| discardLines | MigrationDemo_V1.Data.Line | l1 | s | CLASS_REMOVED | Lines retired |");
  }
}
