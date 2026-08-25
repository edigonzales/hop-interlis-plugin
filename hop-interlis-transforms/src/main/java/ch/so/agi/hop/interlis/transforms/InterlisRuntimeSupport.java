package ch.so.agi.hop.interlis.transforms;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.value.ValueMetaFactory;

/**
 * Runtime support shared by all INTERLIS transforms.
 *
 * <p>The INTERLIS plugin and hop-geometry-type-plugin declare the same Hop classloader group
 * ({@code sogeo-geometry}). Hop creates the group classloader lazily and only merges a plugin's
 * jars into it when that plugin's classes are loaded through the plugin registry. A transform
 * referencing {@code ValueMetaGeometry} directly would therefore fail with
 * {@code NoClassDefFoundError} unless the value meta plugins have been loaded before.
 *
 * <p>{@link #initialize()} triggers exactly that merge by loading the registered value meta plugin
 * classes through the registry, then verifies that the shared geometry value type is available.
 * It is cheap, idempotent and called from the transform meta/runtime entry points before any
 * {@code ValueMetaGeometry} or JTS class is referenced.
 */
public final class InterlisRuntimeSupport {

  private static final String VALUE_META_GEOMETRY_CLASS =
      "com.atolcd.hop.core.row.value.ValueMetaGeometry";

  private static volatile boolean initialized;

  private InterlisRuntimeSupport() {}

  /** Merges the shared classloader group and verifies the geometry plugin is available. */
  public static synchronized void initialize() throws HopException {
    if (initialized) {
      return;
    }

    try {
      ValueMetaFactory.getValueMetaPluginClasses();
    } catch (HopPluginException e) {
      throw new HopException("Failed to load Hop value meta plugins: " + e.getMessage(), e);
    }

    boolean geometryTypeAvailable;
    try {
      Class.forName(VALUE_META_GEOMETRY_CLASS);
      geometryTypeAvailable = true;
    } catch (ClassNotFoundException e) {
      geometryTypeAvailable = false;
    }

    if (!geometryTypeAvailable) {
      throw new HopException(
          "INTERLIS plugin requires hop-geometry-type-plugin. "
              + "Install the matching Geometry Type Plugin and restart Apache Hop.");
    }

    initialized = true;
  }
}

