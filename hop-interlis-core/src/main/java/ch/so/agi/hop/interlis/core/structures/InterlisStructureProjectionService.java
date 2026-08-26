package ch.so.agi.hop.interlis.core.structures;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves models and projects a multi-valued structure attribute of a class into a
 * {@link InterlisStructurePlan}.
 *
 * <p>Shared by the transform meta ({@code getFields()}/{@code check()}), the transform runtime
 * and the GUI probe of INTERLIS Structure Explode and INTERLIS Structure Collect.
 */
public final class InterlisStructureProjectionService {

  private final InterlisModelService modelService;
  private final InterlisStructureLocator structureLocator;

  public InterlisStructureProjectionService() {
    this(new InterlisModelServiceImpl(), new InterlisStructureLocator());
  }

  public InterlisStructureProjectionService(
      InterlisModelService modelService, InterlisStructureLocator structureLocator) {
    this.modelService = modelService;
    this.structureLocator = structureLocator;
  }

  /**
   * Resolves the models and builds the structure plan.
   *
   * @throws InterlisModelException if models or class cannot be resolved
   * @throws InterlisMappingException if the structure path cannot be projected
   */
  public InterlisStructureProjectionResult project(
      InterlisModelRequest request,
      String className,
      String structurePath,
      ProjectionOptions options)
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

    CompiledInterlisModel model =
        modelService.compile(
            new ModelSource(List.of(), modelNames, request.modelDirectories()),
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
                        "Class " + className + " was not found in models " + modelNames
                            + "; available classes: "
                            + schema.classes().stream()
                                .map(InterlisClassDescriptor::scopedName)
                                .sorted()
                                .toList()));

    InterlisStructurePlan plan =
        structureLocator.locate(schema, classDescriptor, structurePath, options);
    return new InterlisStructureProjectionResult(model, schema, plan, modelNames);
  }
}
