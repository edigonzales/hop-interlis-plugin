package ch.so.agi.hop.interlis.transforms.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class InterlisTestPluginContractTest {

  @Test
  void transform_is_registered_with_expected_id_and_classloader_group() {
    Transform annotation = InterlisTestMeta.class.getAnnotation(Transform.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.id()).isEqualTo("INTERLIS_TEST");
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
  }

  @Test
  void transform_declares_an_icon() {
    Transform annotation = InterlisTestMeta.class.getAnnotation(Transform.class);

    assertThat(annotation.image())
        .isEqualTo("ch/so/agi/hop/interlis/transforms/test/icons/interlis-test.svg");
    assertThat(InterlisTestMeta.class.getResource("/" + annotation.image())).isNotNull();
  }
}
