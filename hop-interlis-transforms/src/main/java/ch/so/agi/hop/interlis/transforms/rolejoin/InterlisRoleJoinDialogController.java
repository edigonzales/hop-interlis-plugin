package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.transforms.InterlisPreviewRow;
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
