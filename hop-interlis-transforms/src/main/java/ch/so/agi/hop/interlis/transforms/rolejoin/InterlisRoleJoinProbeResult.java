package ch.so.agi.hop.interlis.transforms.rolejoin;

import ch.so.agi.hop.interlis.core.model.CompiledInterlisModel;
import ch.so.agi.hop.interlis.core.model.InterlisClassDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisRoleDescriptor;
import ch.so.agi.hop.interlis.core.model.InterlisSchemaDescriptor;

/**
 * Result of probing the model configuration of INTERLIS Role Join.
 *
 * @param model the compiled model
 * @param schema the extracted schema
 * @param mainClass the main stream's class
 * @param role the selected role
 * @param target the role's target class
 */
public record InterlisRoleJoinProbeResult(
    CompiledInterlisModel model,
    InterlisSchemaDescriptor schema,
    InterlisClassDescriptor mainClass,
    InterlisRoleDescriptor role,
    InterlisClassDescriptor target) {}
