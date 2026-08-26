package ch.so.agi.hop.interlis.transforms.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopValueException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValueMetaInterlisObjectTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void plugin_contract() {
    ValueMetaInterlisObject meta = new ValueMetaInterlisObject();
    assertThat(meta.getType()).isEqualTo(ValueMetaInterlisObject.TYPE_INTERLIS_OBJECT);
    assertThat(meta.getTypeDesc()).isEqualTo("InterlisObject");
  }

  @Test
  void clone_is_independent() {
    ValueMetaInterlisObject meta = new ValueMetaInterlisObject("field");
    ValueMetaInterlisObject clone = meta.clone();
    assertThat(clone.getName()).isEqualTo("field");
    assertThat(clone).isNotSameAs(meta);
  }

  @Test
  void clone_value_data_deep_copies_the_object() throws Exception {
    Iom_jObject object = new Iom_jObject("HopIli_Structures_V1.Data.Person", "p1");
    object.setattrvalue("Name", "Meier");
    Iom_jObject child = new Iom_jObject("HopIli_Structures_V1.Data.Address", null);
    child.setattrvalue("Street", "Main Street");
    object.addattrobj("Addresses", child);

    ValueMetaInterlisObject meta = new ValueMetaInterlisObject();
    IomObject clone = (IomObject) meta.cloneValueData(object);

    assertThat(clone).isNotSameAs(object);
    assertThat(clone.getobjectoid()).isEqualTo("p1");
    assertThat(clone.getattrobj("Addresses", 0)).isNotSameAs(child);
    assertThat(clone.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("Main Street");

    // Mutating the clone must not affect the original.
    clone.getattrobj("Addresses", 0).setattrvalue("Street", "Changed");
    assertThat(child.getattrvalue("Street")).isEqualTo("Main Street");
  }

  @Test
  void null_value_data_clones_to_null() throws Exception {
    assertThat(new ValueMetaInterlisObject().cloneValueData(null)).isNull();
  }

  @Test
  void get_string_renders_the_object_for_preview() throws Exception {
    Iom_jObject object = new Iom_jObject("HopIli_Structures_V1.Data.Person", "p1");
    object.setattrvalue("Name", "Meier");

    String string = new ValueMetaInterlisObject().getString(object);

    assertThat(string).contains("HopIli_Structures_V1.Data.Person").contains("Meier");
    assertThat(new ValueMetaInterlisObject().getString(null)).isNull();
  }

  @Test
  void rejects_foreign_value_type() {
    ValueMetaInterlisObject meta = new ValueMetaInterlisObject();
    assertThatThrownBy(() -> meta.getString("not an IomObject"))
        .isInstanceOf(HopValueException.class)
        .hasMessageContaining("IomObject");
    assertThatThrownBy(() -> meta.cloneValueData("not an IomObject"))
        .isInstanceOf(HopValueException.class)
        .hasMessageContaining("IomObject");
  }

  @Test
  void native_data_type_passthrough() throws Exception {
    Iom_jObject object = new Iom_jObject("HopIli_Structures_V1.Data.Person", "p1");
    ValueMetaInterlisObject meta = new ValueMetaInterlisObject();
    assertThat(meta.getNativeDataType(object)).isSameAs(object);
    assertThat(meta.getNativeDataType("other")).isNull();
  }

  @Test
  void binary_roundtrip_preserves_the_object() throws Exception {
    Iom_jObject object = new Iom_jObject("HopIli_Structures_V1.Data.Person", "p1");
    object.setattrvalue("Name", "Meier");
    Iom_jObject address = new Iom_jObject("HopIli_Structures_V1.Data.Address", null);
    address.setattrvalue("Street", "Main Street");
    object.addattrobj("Addresses", address);

    ValueMetaInterlisObject meta = new ValueMetaInterlisObject();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    meta.writeData(new DataOutputStream(buffer), object);
    meta.writeData(new DataOutputStream(buffer), null);

    DataInputStream input = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    IomObject restored = (IomObject) meta.readData(input);
    Object nullRestored = meta.readData(input);

    assertThat(restored.getobjectoid()).isEqualTo("p1");
    assertThat(restored.getattrvalue("Name")).isEqualTo("Meier");
    assertThat(restored.getattrobj("Addresses", 0).getattrvalue("Street")).isEqualTo("Main Street");
    assertThat(nullRestored).isNull();
  }
}
