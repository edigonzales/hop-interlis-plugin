package ch.so.agi.hop.interlis.transforms.rolejoin;

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
        "Lookup TID     " + (meta.getLookupTidField() == null ? "_ili_tid" : meta.getLookupTidField()),
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

  /** Parses a comma separated field list. */
  public static List<String> parseCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(n -> !n.isEmpty()).toList();
  }
}
