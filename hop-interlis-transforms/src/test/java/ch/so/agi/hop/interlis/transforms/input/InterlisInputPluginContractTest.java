package ch.so.agi.hop.interlis.transforms.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class InterlisInputPluginContractTest {

  @Test
  void transform_is_registered_with_expected_id_and_classloader_group() {
    Transform annotation = InterlisInputMeta.class.getAnnotation(Transform.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.id()).isEqualTo("INTERLIS_INPUT");
    assertThat(annotation.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(annotation.categoryDescription()).isEqualTo("Geospatial");
  }

  @Test
  void transform_declares_an_icon() {
    Transform annotation = InterlisInputMeta.class.getAnnotation(Transform.class);

    assertThat(annotation.image())
        .isEqualTo("ch/so/agi/hop/interlis/transforms/input/icons/interlis-input.svg");
    assertThat(InterlisInputMeta.class.getResource("/" + annotation.image())).isNotNull();
  }
}
