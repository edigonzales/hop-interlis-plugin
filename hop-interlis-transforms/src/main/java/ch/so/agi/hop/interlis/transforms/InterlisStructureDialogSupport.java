package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelService;
import ch.so.agi.hop.interlis.core.model.InterlisModelServiceImpl;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaExtractor;
import ch.so.agi.hop.interlis.core.model.ModelCompileOptions;
import ch.so.agi.hop.interlis.core.model.ModelSource;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureLocator;
import ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionResult;
import java.util.List;

/**
 * Shared model probing for the INTERLIS Structure Explode and Collect dialogs.
 *
 * <p>This is a thin composition of the core services; the dialogs render whatever the probe
 * returns, so probing failures never make a dialog unusable.
 */
public final class InterlisStructureDialogSupport {

  private InterlisStructureDialogSupport() {}

  /**
   * Probes models, class and structure path.
   *
   * @param modelNames explicit model names (empty = not yet configured)
   * @param modelDirectories model repository directories
   * @param className selected class (may be blank)
   * @param structurePath selected structure path (may be blank)
   * @param options projection options for the child fields
   */
  public static InterlisStructureProbeResult probe(
      List<String> modelNames,
      List<String> modelDirectories,
      String className,
      String structurePath,
      ProjectionOptions options) {
    if (modelNames == null || modelNames.isEmpty()) {
      return new InterlisStructureProbeResult(
          false,
          "Model probe unavailable: no INTERLIS models configured.",
          null,
          List.of(),
          List.of());
    }
    try {
      InterlisModelService modelService = new InterlisModelServiceImpl();
      CompiledInterlisModel model =
          modelService.compile(
              new ModelSource(List.of(), modelNames, modelDirectories),
              ModelCompileOptions.defaults());
      InterlisSchemaDescriptor schema =
          new InterlisSchemaExtractor().extract(model.transferDescription());
      List<InterlisClassDescriptor> classes = schema.selectableClasses();

      if (className == null || className.isBlank()) {
        return new InterlisStructureProbeResult(
            true,
            "Model loaded: " + modelNames + "; " + classes.size() + " classes",
            null,
            classes,
            List.of());
      }
      InterlisClassDescriptor classDescriptor = schema.findClass(className).orElse(null);
      if (classDescriptor == null) {
        return new InterlisStructureProbeResult(
            true,
            "Class " + className + " not found in models " + modelNames,
            null,
            classes,
            List.of());
      }
      InterlisStructureLocator locator = new InterlisStructureLocator();
      List<String> structurePaths = locator.multiValuedStructurePaths(schema, classDescriptor);

      if (structurePath == null || structurePath.isBlank()) {
        return new InterlisStructureProbeResult(
            true,
            "Class " + className + " loaded; " + structurePaths.size()
                + " multi-valued structure(s)",
            null,
            classes,
            structurePaths);
      }
      InterlisStructureProjectionResult projection =
          new ch.so.agi.hop.interlis.core.structures.InterlisStructureProjectionService(
                  modelService, locator)
              .project(
                  new ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest(
                      null, modelNames, modelDirectories),
                  className,
                  structurePath,
                  options);
      return new InterlisStructureProbeResult(
          true,
          "Structure " + structurePath + " projects "
              + projection.plan().childFields().size() + " child fields",
          projection,
          classes,
          structurePaths);
    } catch (Exception e) {
      return new InterlisStructureProbeResult(
          false, rootCauseMessage(e), null, List.of(), List.of());
    }
  }

  /** The most relevant cause message for display in the dialog. */
  public static String rootCauseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : message;
  }
}
