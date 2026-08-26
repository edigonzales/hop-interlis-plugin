package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.core.TestResources;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisEnumerationExtractorTest {

  private final InterlisEnumerationExtractor extractor = new InterlisEnumerationExtractor();

  private CompiledInterlisModel compileEnums() throws Exception {
    return new InterlisModelServiceImpl()
        .compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Enums_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());
  }

  @Test
  void flattens_domain_and_inline_enumerations() throws Exception {
    List<InterlisEnumerationRow> rows = extractor.extract(compileEnums());

    // The inline enum on Sample.Nested plus the Kind_ domain alias resolve to the same enum.
    assertThat(rows)
        .extracting(InterlisEnumerationRow::value)
        .contains("one", "two", "three", "alpha", "beta", "beta_1", "beta_2", "gamma");
    assertThat(rows).allSatisfy(row -> assertThat(row.definition()).startsWith("HopIli_Enums_V1"));
  }

  @Test
  void marks_sub_enumerations_with_parent_depth_and_leaf() throws Exception {
    List<InterlisEnumerationRow> rows = extractor.extract(compileEnums());

    InterlisEnumerationRow beta = row(rows, "beta");
    assertThat(beta.parentValue()).isNull();
    assertThat(beta.depth()).isZero();
    assertThat(beta.isLeaf()).isFalse();

    InterlisEnumerationRow beta1 = row(rows, "beta_1");
    assertThat(beta1.parentValue()).isEqualTo("beta");
    assertThat(beta1.depth()).isEqualTo(1);
    assertThat(beta1.isLeaf()).isTrue();
    assertThat(beta1.path()).isEqualTo(beta1.definition() + ".beta.beta_1");
  }

  @Test
  void skips_the_predefined_interlis_model() throws Exception {
    List<InterlisEnumerationRow> rows = extractor.extract(compileEnums());

    assertThat(rows)
        .noneMatch(row -> row.definition().startsWith("INTERLIS."));
  }

  private static InterlisEnumerationRow row(List<InterlisEnumerationRow> rows, String value) {
    return rows.stream().filter(r -> r.value().equals(value)).findFirst().orElseThrow();
  }
}
