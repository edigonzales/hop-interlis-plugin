package ch.so.agi.hop.interlis.transforms.collect;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.*;

class InterlisStructureCollectBindingsTest {
  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void updated_carrier_has_normal_output_storage_and_parent_fields_stay_unchanged()
      throws Exception {
    var input = new RowMeta();
    input.addValueMeta(new ValueMetaString("_ili_tid"));
    var carrier = new ValueMetaInterlisObject("_ili_source_object");
    var object = new Iom_jObject("Model.Topic.Class", "p1");
    carrier.setStorageType(IValueMeta.STORAGE_TYPE_INDEXED);
    carrier.setIndex(new Object[] {object});
    input.addValueMeta(carrier);
    var meta = new InterlisStructureCollectMeta();
    meta.setDefault();
    var binding = InterlisStructureCollectBindings.parent(input, meta, new Variables());
    assertThat(binding.carrier().read(new Object[] {"p1", 0})).isSameAs(object);
    var output = input.clone();
    meta.getFields(output, "collect", null, null, new Variables(), null);
    assertThat(output.getFieldNames()).containsExactly(input.getFieldNames());
    assertThat(output.getValueMeta(1).getStorageType()).isEqualTo(IValueMeta.STORAGE_TYPE_NORMAL);
    assertThat(input.getValueMeta(1).getStorageType()).isEqualTo(IValueMeta.STORAGE_TYPE_INDEXED);
    assertThat(binding.output().getValueMeta(1).getStorageType())
        .isEqualTo(IValueMeta.STORAGE_TYPE_NORMAL);
  }

  @Test
  void parent_key_must_be_string_even_if_a_numeric_value_would_be_convertible() throws Exception {
    var input = new RowMeta();
    input.addValueMeta(new ValueMetaInteger("_ili_tid"));
    input.addValueMeta(new ValueMetaInterlisObject("_ili_source_object"));
    var meta = new InterlisStructureCollectMeta();
    meta.setDefault();
    assertThatThrownBy(() -> InterlisStructureCollectBindings.parent(input, meta, new Variables()))
        .hasMessageContaining("parent")
        .hasMessageContaining("expected String, actual Integer");
  }
}
