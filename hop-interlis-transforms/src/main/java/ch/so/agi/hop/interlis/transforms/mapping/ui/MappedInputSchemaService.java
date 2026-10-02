package ch.so.agi.hop.interlis.transforms.mapping.ui;

import ch.so.agi.hop.interlis.core.mapping.*;
import ch.so.agi.hop.interlis.core.structures.*;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import java.nio.file.Path;
import java.util.*;
import org.apache.hop.core.variables.IVariables;

/** Headless design-time service; SWT only renders its result. */
public final class MappedInputSchemaService {
  public record Result(List<String> classes, List<InterlisFieldPlan> fields) {}

  public Result inspect(
      String models,
      String directories,
      String original,
      InterlisMappedInput input,
      IVariables variables)
      throws Exception {
    String source = InterlisFieldBinding.resolve(variables, original);
    var service = new InterlisProjectionService();
    var context =
        service.loadModel(
            new InterlisModelRequest(
                source.isBlank() ? null : Path.of(source),
                InterlisModelSourceSupport.parseModelNames(
                    InterlisFieldBinding.resolve(variables, models)),
                InterlisModelSourceSupport.parseModelDirectories(
                    InterlisFieldBinding.resolve(variables, directories))));
    var classes = new ArrayList<String>();
    context.schema().classes().stream()
        .filter(c -> !c.isAbstract())
        .forEach(c -> classes.add(c.scopedName()));
    if (original == null) context.schema().associations().forEach(c -> classes.add(c.scopedName()));
    Collections.sort(classes);
    String cls = InterlisFieldBinding.resolve(variables, input.getClassName());
    if (cls.isBlank()) return new Result(classes, List.of());
    String path = InterlisFieldBinding.resolve(variables, input.getStructurePath());
    var options =
        new ProjectionOptions(
            true, true, false, false, false, true, "_", null, Set.of(), true, true);
    var fields =
        path.isBlank()
            ? service.project(context, cls, options).plan().fields()
            : new InterlisStructureProjectionService()
                .project(context, cls, path, options)
                .plan()
                .childFields();
    return new Result(
        classes,
        fields.stream()
            .filter(
                f ->
                    f.source() != InterlisFieldSource.OBJECT_ID
                        && f.source() != InterlisFieldSource.BASKET_ID)
            .filter(
                f ->
                    original == null
                        || ch.so.agi.hop.interlis.core.update.InterlisPatchPlan.canEdit(f))
            .toList());
  }
}
