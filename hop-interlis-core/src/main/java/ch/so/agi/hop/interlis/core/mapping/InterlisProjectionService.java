package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves models and projects a class to a {@link InterlisRowMappingPlan}.
 *
 * <p>This service is shared by the transform meta ({@code getFields()}/{@code check()}), the
 * transform runtime and the GUI probe: all consumers work on the same plan, so the schema cannot
 * diverge between design time and runtime.
 */
public final class InterlisProjectionService {

  private static final String XTF_DIR_PLACEHOLDER = "%XTF_DIR";

  private final InterlisModelService modelService;
  private final InterlisRowSchemaBuilder schemaBuilder;

  public InterlisProjectionService() {
    this(new InterlisModelServiceImpl(), new InterlisRowSchemaBuilder());
  }

  public InterlisProjectionService(
      InterlisModelService modelService, InterlisRowSchemaBuilder schemaBuilder) {
    this.modelService = modelService;
    this.schemaBuilder = schemaBuilder;
  }

  /**
   * Resolves the models (explicit or detected from the transfer file) and builds the projection
   * for the given class.
   *
   * @throws InterlisModelException if models or class cannot be resolved
   * @throws InterlisMappingException if the projection cannot be built
   */
  public InterlisProjectionResult project(
      InterlisModelRequest request, String className, ProjectionOptions options)
      throws InterlisModelException, InterlisMappingException {
    List<String> modelNames = new ArrayList<>(request.modelNames());
    if (modelNames.isEmpty() && request.dataFile() != null) {
      modelNames.addAll(modelService.detectModelNames(request.dataFile()));
    }
    if (modelNames.isEmpty()) {
      throw new InterlisModelException(
          "No INTERLIS models configured and none could be detected from transfer file "
              + (request.dataFile() == null ? "(no file given)" : request.dataFile()));
    }

    List<String> modelDirectories = resolveModelDirectories(request);
    CompiledInterlisModel model =
        modelService.compile(
            new ModelSource(List.of(), modelNames, modelDirectories),
            ModelCompileOptions.defaults());
    InterlisSchemaDescriptor schema =
        new InterlisSchemaExtractor().extract(model.transferDescription());

    if (className == null || className.isBlank()) {
      throw new InterlisModelException("No INTERLIS class selected");
    }
    InterlisClassDescriptor classDescriptor =
        schema
            .findClass(className)
            .orElseThrow(
                () ->
                    new InterlisModelException(
                        "Class "
                            + className
                            + " was not found in models "
                            + modelNames
                            + "; available classes: "
                            + schema.classes().stream()
                                .map(InterlisClassDescriptor::scopedName)
                                .sorted()
                                .toList()));

    InterlisRowMappingPlan plan = schemaBuilder.build(schema, classDescriptor, options);
    return new InterlisProjectionResult(model, schema, plan, modelNames);
  }

  private List<String> resolveModelDirectories(InterlisModelRequest request) {
    List<String> resolved = new ArrayList<>();
    for (String directory : request.modelDirectories()) {
      String value = directory.trim();
      if (XTF_DIR_PLACEHOLDER.equals(value)) {
        if (request.dataFile() == null) {
          continue;
        }
        Path parent = request.dataFile().toAbsolutePath().getParent();
        if (parent != null) {
          value = parent.toString();
        }
      }
      if (!value.isEmpty()) {
        resolved.add(value);
      }
    }
    return resolved;
  }
}
