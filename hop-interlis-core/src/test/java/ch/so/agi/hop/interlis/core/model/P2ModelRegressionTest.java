package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P2ModelRegressionTest {
  @TempDir Path dir;
  final InterlisModelServiceImpl service = new InterlisModelServiceImpl();

  String model(String name, String body) {
    return "INTERLIS 2.3; MODEL "
        + name
        + " (en) AT \"https://example.org\" VERSION \"1\" = "
        + body
        + " END "
        + name
        + ".";
  }

  @Test
  void file_names_do_not_determine_declared_model_names() throws Exception {
    Path file = dir.resolve("arbitrary.ili");
    Files.writeString(file, model("Declared", "TOPIC T = CLASS C = Name : TEXT*20; END C; END T;"));
    var compiled =
        service.compile(
            new ModelSource(List.of(file), List.of(), List.of(dir.toString())),
            ModelCompileOptions.defaults());
    assertThat(compiled.compiledModelNames()).contains("Declared").doesNotContain("arbitrary");
  }

  @Test
  void detects_changed_import_even_with_same_timestamp_and_reuses_unchanged_model()
      throws Exception {
    Path imported = dir.resolve("Base.ili");
    Files.writeString(imported, model("Base", "DOMAIN Label = TEXT*20;"));
    Files.writeString(
        dir.resolve("Main.ili"),
        model("Main", "IMPORTS Base; TOPIC T = CLASS C = Name : Base.Label; END C; END T;"));
    var request = new ModelSource(List.of(), List.of("Main"), List.of(dir.toString()));
    var first = service.compile(request, ModelCompileOptions.defaults());
    assertThat(service.compile(request, ModelCompileOptions.defaults())).isSameAs(first);
    var timestamp = Files.getLastModifiedTime(imported);
    Files.writeString(imported, model("Base", "DOMAIN Label = TEXT*40;"));
    Files.setLastModifiedTime(imported, timestamp);
    assertThat(service.compile(request, ModelCompileOptions.defaults())).isNotSameAs(first);
    Files.delete(imported);
    assertThatThrownBy(() -> service.compile(request, ModelCompileOptions.defaults()))
        .isInstanceOf(InterlisModelException.class);
  }

  @Test
  void reload_recompiles_and_enumerations_include_unused_model_domains_and_association_attributes()
      throws Exception {
    Path file = dir.resolve("Enums.ili");
    Files.writeString(
        file,
        model(
            "Enums",
            """
        DOMAIN Unused = (one, two); ColorType = (a, b);
        TOPIC T =
          CLASS A = Kind : ColorType; END A;
          CLASS B = END B;
          ASSOCIATION AB = a -- {0..*} A; b -- {0..*} B; Kind : (first, second); END AB;
        END T;
        """));
    var request = new ModelSource(List.of(file), List.of(), List.of(dir.toString()));
    var compiled = service.compile(request, ModelCompileOptions.defaults());
    assertThat(service.reload(request, ModelCompileOptions.defaults())).isNotSameAs(compiled);
    var rows = new InterlisEnumerationExtractor().extract(compiled);
    assertThat(rows)
        .extracting(InterlisEnumerationRow::definition)
        .contains("Enums.Unused", "Enums.ColorType", "Enums.T.AB.Kind")
        .doesNotContain("Enums.T.A.Kind", "Enums.ColorAlias");
  }
}
