package ch.so.agi.hop.interlis.core.model;

import ch.interlis.ili2c.metamodel.DataModel;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.RefSystemModel;
import ch.interlis.ili2c.metamodel.SymbologyModel;
import ch.interlis.ili2c.metamodel.TypeModel;

/** Kind of an INTERLIS model as declared by the ili2c metamodel. */
public enum InterlisModelKind {
  DATA,
  TYPE,
  REFSYSTEM,
  SYMBOLOGY,
  PREDEFINED,
  OTHER;

  public static InterlisModelKind from(Model model) {
    if (model instanceof DataModel) {
      return DATA;
    }
    if (model instanceof TypeModel) {
      return TYPE;
    }
    if (model instanceof RefSystemModel) {
      return REFSYSTEM;
    }
    if (model instanceof SymbologyModel) {
      return SYMBOLOGY;
    }
    if (model instanceof PredefinedModel) {
      return PREDEFINED;
    }
    return OTHER;
  }

  /** Whether a root from this model kind should be offered in class selectors. */
  public boolean isSelectable() {
    // A class selector is a user-facing data entry point.  Unknown model kinds are retained in
    // the descriptor for internal resolution, but must not accidentally become selectable.
    return this == DATA;
  }
}
