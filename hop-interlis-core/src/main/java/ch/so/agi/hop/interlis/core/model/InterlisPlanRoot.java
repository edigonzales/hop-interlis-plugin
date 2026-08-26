package ch.so.agi.hop.interlis.core.model;

import java.util.List;

/**
 * A model element that can be projected onto typed Hop rows: a transferable class or an
 * association (link objects).
 *
 * <p>Implemented by {@link InterlisClassDescriptor} and {@link InterlisAssociationDescriptor} so
 * that the row schema builder, the mappers and the transforms treat classes and association rows
 * uniformly.
 */
public interface InterlisPlanRoot {

  /** Unqualified name, e.g. {@code Building} or {@code BuildingMunicipality}. */
  String name();

  /** Qualified name, e.g. {@code Model.Topic.Class}. */
  String scopedName();

  /** Qualified topic name, e.g. {@code Model.Topic}. */
  String topicScopedName();

  /** All properties in stable model order (attributes plus roles). */
  List<InterlisPropertyDescriptor> effectiveProperties();
}
