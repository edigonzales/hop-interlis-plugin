package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.mapping.InterlisMappingException;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisModelException;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
import ch.so.agi.hop.interlis.transforms.InterlisProbeStatus;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.mapping.InterlisFieldBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;

/** SWT-free controller for the INTERLIS Role Join dialog: model probing and preview formatting. */
public final class InterlisRoleJoinDialogController {

  record DialogProbe(
      List<String> classes,
      List<String> roles,
      InterlisRoleJoinProbeResult selectedRole,
      InterlisProbeStatus status,
      String message) {}

  DialogProbe probe(InterlisRoleJoinMeta meta, IVariables variables) throws InterlisModelException {
    var context = meta.loadModel(variables);
    var classes = context.schema().selectableClasses().stream()
        .filter(descriptor -> !descriptor.isAbstract())
        .map(InterlisClassDescriptor::scopedName)
        .toList();

    String className = meta.resolvedMainClassName(variables);
    if (className.isBlank()) {
      return new DialogProbe(
          classes, List.of(), null, InterlisProbeStatus.INFO, "Model loaded. Select a main class.");
    }
    InterlisClassDescriptor mainClass;
    try {
      mainClass = InterlisRoleJoinMeta.requireMainClass(context, className);
    } catch (InterlisModelException e) {
      return new DialogProbe(classes, List.of(), null, InterlisProbeStatus.ERROR, e.getMessage());
    }

    var roles = roleNames(mainClass);
    String role = meta.resolvedRoleName(variables);
    if (role.isBlank()) {
      String message = roles.isEmpty()
          ? "No roles are available on class " + className + "."
          : "Model loaded. Select a role.";
      return new DialogProbe(classes, roles, null, InterlisProbeStatus.INFO, message);
    }
    try {
      var selected = InterlisRoleJoinMeta.probeRole(context, mainClass, role);
      return new DialogProbe(
          classes, roles, selected, InterlisProbeStatus.SUCCESS,
          "Model loaded; role " + selected.role().name() + " -> " + selected.target().scopedName());
    } catch (InterlisModelException | InterlisMappingException e) {
      return new DialogProbe(classes, roles, null, InterlisProbeStatus.ERROR, e.getMessage());
    }
  }

  /** All roles available on the configured main class, with their targets. */
  private List<String> roleNames(InterlisClassDescriptor mainClass) {
    return mainClass.roles().stream()
        .map(
            role ->
                role.name()
                    + " -> "
                    + (role.targetClassScopedName() == null ? "?" : role.targetClassScopedName())
                    + " "
                    + role.cardinality())
        .toList();
  }

  String selectedRoleLabel(List<String> choices, String current) {
    String name = roleName(current);
    return choices.stream()
        .filter(choice -> roleName(choice).equals(name))
        .findFirst()
        .orElse(current);
  }

  static String roleName(String label) {
    int target = label.indexOf(" -> ");
    return target < 0 ? label : label.substring(0, target).trim();
  }

  /** Previews the key fields and the same target projection used by the runtime. */
  public InterlisSchemaPreview createSchemaPreview(
      InterlisRoleJoinMeta meta, InterlisRoleJoinProbeResult result, IVariables vars)
      throws HopTransformException {
    String reference = InterlisFieldBinding.resolve(vars, meta.resolvedMainReferenceField(result));
    var rows =
        new ArrayList<>(
            List.of(
                new InterlisPreviewRow("Main class", "", result.mainClass().scopedName()),
                new InterlisPreviewRow(
                    "Role",
                    result.role().cardinality().toString(),
                    result.role().name() + " -> " + result.target().scopedName()),
                new InterlisPreviewRow("Main BID field", "String", InterlisEnvelopeRowLayout.BID),
                new InterlisPreviewRow("Main reference field", "String", reference),
                new InterlisPreviewRow(
                    "Reference BID field",
                    "String",
                    reference
                        + "_bid"
                        + (result.role().external() ? " (required for EXTERNAL)" : " (optional)")),
                new InterlisPreviewRow("Lookup BID field", "String", InterlisEnvelopeRowLayout.BID),
                new InterlisPreviewRow(
                    "Lookup TID field",
                    "String",
                    InterlisFieldBinding.resolve(vars, meta.getLookupTidField()))));
    for (var field :
        InterlisRoleJoinBindings.output(new RowMeta(), result, meta, vars).getValueMetaList()) {
      rows.add(new InterlisPreviewRow(field.getName(), field.getTypeDesc(), "lookup field"));
    }
    return new InterlisSchemaPreview(rows, List.of(), null);
  }

  /** Parses a comma separated field list. */
  public static List<String> parseCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(n -> !n.isEmpty()).toList();
  }
}
