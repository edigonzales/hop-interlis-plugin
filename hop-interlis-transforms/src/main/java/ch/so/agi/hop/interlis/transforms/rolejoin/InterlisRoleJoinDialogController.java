package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.model.InterlisAttributeDescriptor;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreview;
import ch.so.agi.hop.interlis.transforms.InterlisSchemaPreviewSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

/**
 * SWT-free controller for the INTERLIS Role Join dialog: model probing and preview formatting.
 */
public final class InterlisRoleJoinDialogController {

  /** Probes the current configuration; failures are returned as a friendly message. */
  public InterlisRoleJoinProbeResult probe(InterlisRoleJoinMeta meta, IVariables variables)
      throws Exception {
    return meta.probeRole(variables);
  }

  /** All roles available on the configured main class, with their targets. */
  public List<String> roleNames(InterlisRoleJoinProbeResult result) {
    return result.mainClass().roles().stream()
        .map(
            role ->
                role.name()
                    + " -> "
                    + (role.targetClassScopedName() == null ? "?" : role.targetClassScopedName())
                    + " "
                    + role.cardinality())
        .toList();
  }

  /** Formats a preview of the join configuration. */
  public String formatPreview(InterlisRoleJoinMeta meta, InterlisRoleJoinProbeResult result) {
    return String.join(
        "\n",
        "Role Join preview",
        "----------------",
        "Main class     " + result.mainClass().scopedName(),
        "Role           " + result.role().name() + " -> " + result.target().scopedName()
            + " " + result.role().cardinality(),
        "Main ref field " + meta.resolvedMainReferenceField(result),
        "Lookup TID     "
            + (meta.getLookupTidField() == null ? "_ili_tid" : meta.getLookupTidField()),
        "Fields added   "
            + meta.effectiveLookupFields(result).stream()
                .map(f -> meta.resolvedPrefix(result) + f)
                .toList(),
        "Target fields  " + result.target().attributes().stream()
            .map(a -> a.name() + " (" + a.typeName() + ")").toList(),
        "",
        "The lookup stream is loaded into memory once (max "
            + meta.getMaxLookupRows() + " rows).");
  }

  /** Builds the structured field/mapping preview shown by the dialog. */
  public InterlisSchemaPreview createSchemaPreview(
      InterlisRoleJoinMeta meta, InterlisRoleJoinProbeResult result) {
    String prefix = meta.resolvedPrefix(result);
    List<InterlisPreviewRow> prefixRows =
        new ArrayList<>(
            List.of(
                new InterlisPreviewRow("Main class", "", result.mainClass().scopedName()),
                new InterlisPreviewRow(
                    "Role",
                    result.role().cardinality().toString(),
                    result.role().name() + " -> " + result.target().scopedName()),
                new InterlisPreviewRow(
                    "Main reference field", "", meta.resolvedMainReferenceField(result)),
                new InterlisPreviewRow(
                    "Lookup TID field", "", safe(meta.getLookupTidField(), "_ili_tid"))));
    List<String> warnings = new ArrayList<>();
    List<ch.so.agi.hop.interlis.core.mapping.InterlisFieldPlan> fields = new ArrayList<>();
    for (String name : meta.effectiveLookupFields(result)) {
      InterlisAttributeDescriptor attribute =
          result.target().attributes().stream()
              .filter(candidate -> candidate.name().equals(name))
              .findFirst()
              .orElse(null);
      if (attribute == null) {
        warnings.add("Lookup field " + name + " was not found on " + result.target().scopedName());
        continue;
      }
      fields.add(InterlisSchemaPreviewSupport.attributeFieldPlan(prefix + attribute.name(), attribute));
    }
    return InterlisSchemaPreviewSupport.createFieldPreview(prefixRows, fields, warnings);
  }

  private static String safe(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /** Parses a comma separated field list. */
  public static List<String> parseCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(n -> !n.isEmpty()).toList();
  }
}
