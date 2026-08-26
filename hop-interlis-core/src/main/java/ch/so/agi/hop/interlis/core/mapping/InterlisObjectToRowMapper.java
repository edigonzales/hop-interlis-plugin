package ch.so.agi.hop.interlis.core.mapping;

import ch.so.agi.hop.interlis.core.io.InterlisObjectEnvelope;

/**
 * Maps an INTERLIS object envelope to a typed Hop row according to a precomputed
 * {@link InterlisRowMappingPlan}.
 *
 * <p>Implementations must not perform any model analysis per row; the plan carries output
 * indexes, property paths and converters.
 */
public interface InterlisObjectToRowMapper {

  /**
   * Maps one object envelope to a row.
   *
   * @param envelope the transfer object
   * @param mappingPlan the precomputed projection
   * @return the row; never {@code null}
   * @throws InterlisMappingException if a field value cannot be mapped
   */
  Object[] map(InterlisObjectEnvelope envelope, InterlisRowMappingPlan mappingPlan)
      throws InterlisMappingException;

  /**
   * Maps one object envelope to a row with a link lookup for flattened association attributes.
   *
   * @param envelope the transfer object
   * @param mappingPlan the precomputed projection
   * @param linkLookup resolves association link objects; may be {@code null}
   * @return the row; never {@code null}
   * @throws InterlisMappingException if a field value cannot be mapped
   */
  Object[] map(
      InterlisObjectEnvelope envelope,
      InterlisRowMappingPlan mappingPlan,
      InterlisAssociationLinkLookup linkLookup)
      throws InterlisMappingException;
}
