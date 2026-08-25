package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.HopEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterlisRuntimeSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void initialize_merges_classloader_group_and_verifies_geometry_type() throws Exception {
    InterlisRuntimeSupport.initialize();

    // The shared Hop geometry value type must be resolvable afterwards.
    assertThat(Class.forName("com.atolcd.hop.core.row.value.ValueMetaGeometry")).isNotNull();
  }

  @Test
  void initialize_is_idempotent() throws Exception {
    InterlisRuntimeSupport.initialize();
    InterlisRuntimeSupport.initialize();

    assertThat(Class.forName("com.atolcd.hop.core.row.value.ValueMetaGeometry")).isNotNull();
  }
}
