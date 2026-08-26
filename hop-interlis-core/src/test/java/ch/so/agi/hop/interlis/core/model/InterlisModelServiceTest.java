package ch.so.agi.hop.interlis.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.so.agi.hop.interlis.core.TestResources;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterlisModelServiceTest {

  private static final Path MODELS = TestResources.path("/models");

  private final InterlisModelService service = new InterlisModelServiceImpl();

  /** HopIli_Primitives_V1 uses XMLDate/XMLTime, which exist since INTERLIS 2.4. */
  private static ModelCompileOptions primitivesOptions() {
    return new ModelCompileOptions("2.4");
  }

  @Test
  void compile_local_model_returns_transfer_description() throws Exception {
    CompiledInterlisModel model =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
                List.of(),
                List.of()),
            primitivesOptions());

    TransferDescription td = model.transferDescription();
    assertThat(td).isNotNull();
    assertThat(modelName(td, "HopIli_Primitives_V1")).isTrue();
  }

  @Test
  void compile_model_by_name_through_model_directory() throws Exception {
    CompiledInterlisModel model =
        service.compile(
            new ModelSource(
                List.of(), List.of("HopIli_Geometry_V1"), List.of(MODELS.toString())),
            ModelCompileOptions.defaults());

    assertThat(modelName(model.transferDescription(), "HopIli_Geometry_V1")).isTrue();
    assertThat(model.compiledModelNames()).containsExactly("HopIli_Geometry_V1");
  }

  @Test
  void lists_transferable_classes() throws Exception {
    CompiledInterlisModel model =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Geometry_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());

    List<InterlisClassDescriptor> classes = service.listTransferableClasses(model);

    assertThat(classes)
        .extracting(InterlisClassDescriptor::scopedName)
        .contains("HopIli_Geometry_V1.Data.TestObject");
  }

  @Test
  void find_class_by_qualified_name() throws Exception {
    CompiledInterlisModel model =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Spike_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());

    assertThat(service.findClass(model, "HopIli_Spike_V1.Data.Building")).isPresent();
    assertThat(service.findClass(model, "HopIli_Spike_V1.Data.DoesNotExist")).isEmpty();
  }

  @Test
  void unknown_model_fails_with_diagnostic() {
    assertThatThrownBy(
            () ->
                service.compile(
                    new ModelSource(
                        List.of(), List.of("No_Such_Model"), List.of(MODELS.toString())),
                    ModelCompileOptions.defaults()))
        .isInstanceOf(InterlisModelException.class)
        .hasMessageContaining("No_Such_Model")
        .hasMessageContaining("was not found");
  }

  @Test
  void missing_model_file_fails_with_diagnostic() {
    assertThatThrownBy(
            () ->
                service.compile(
                    new ModelSource(
                        List.of(Path.of("/does/not/exist.ili")), List.of(), List.of()),
                    ModelCompileOptions.defaults()))
        .isInstanceOf(InterlisModelException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void model_cache_reuses_same_compilation_result() throws Exception {
    ModelSource source =
        new ModelSource(
            List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
            List.of(),
            List.of());
    CompiledInterlisModel first = service.compile(source, primitivesOptions());
    CompiledInterlisModel second = service.compile(source, primitivesOptions());

    assertThat(second.transferDescription()).isSameAs(first.transferDescription());
  }

  @Test
  void model_cache_is_shared_across_service_instances() throws Exception {
    // The cache is static: every transform creates its own service instance, but the compiled
    // model is shared JVM-wide (design-time, immutable result).
    ModelSource source =
        new ModelSource(
            List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
            List.of(),
            List.of());
    CompiledInterlisModel first =
        new InterlisModelServiceImpl().compile(source, primitivesOptions());
    CompiledInterlisModel second =
        new InterlisModelServiceImpl().compile(source, primitivesOptions());

    assertThat(second.transferDescription()).isSameAs(first.transferDescription());
  }

  @Test
  void model_cache_key_changes_when_model_configuration_changes() throws Exception {
    CompiledInterlisModel first =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Primitives_V1.ili")),
                List.of(),
                List.of()),
            primitivesOptions());
    CompiledInterlisModel second =
        service.compile(
            new ModelSource(
                List.of(TestResources.path("/models/HopIli_Geometry_V1.ili")),
                List.of(),
                List.of()),
            ModelCompileOptions.defaults());

    assertThat(second.transferDescription()).isNotSameAs(first.transferDescription());
  }

  private static boolean modelName(TransferDescription td, String expectedName) {
    java.util.Iterator<ch.interlis.ili2c.metamodel.Model> models = td.iterator();
    while (models.hasNext()) {
      if (expectedName.equals(models.next().getName())) {
        return true;
      }
    }
    return false;
  }
}
