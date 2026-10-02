package ch.so.agi.hop.interlis.core.update;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;
import org.junit.jupiter.api.Test;

class UpdateReferenceTest {
  @Test
  void hash_ignores_attribute_order_and_locations_but_detects_child_order_and_reference_changes() {
    var a = new Iom_jObject("Model.Data.Item", "i1");
    a.setattrvalue("B", "b");
    a.setattrvalue("A", "a");
    a.addattrvalue("Texts", "first");
    a.addattrvalue("Texts", "second");
    var b = new Iom_jObject("Model.Data.Item", "i1");
    b.setattrvalue("A", "a");
    b.setattrvalue("B", "b");
    b.addattrvalue("Texts", "first");
    b.addattrvalue("Texts", "second");
    b.setobjectline(55);
    assertThat(UpdateReference.fingerprint(b)).isEqualTo(UpdateReference.fingerprint(a));
    b.setattrvalue("Texts", 0, "second");
    b.setattrvalue("Texts", 1, "first");
    assertThat(UpdateReference.fingerprint(b)).isNotEqualTo(UpdateReference.fingerprint(a));
    b = new Iom_jObject(a);
    b.setobjectrefbid("different");
    assertThat(UpdateReference.fingerprint(b)).isNotEqualTo(UpdateReference.fingerprint(a));
    var reference =
        new UpdateReference(
            a.getobjecttag(), "b1", "i1", "Holder.Children", 1, UpdateReference.fingerprint(a));
    assertThat(UpdateReference.decode(reference.encode())).isEqualTo(reference);
    assertThatThrownBy(() -> UpdateReference.decode("not-a-reference"))
        .hasMessageContaining("Invalid");
    assertThatThrownBy(() -> UpdateReference.decode(reference.encode() + "AA"))
        .hasMessageContaining("Invalid");
  }

  @Test
  void identity_encoding_has_no_separator_collisions() {
    assertThat(UpdateReference.key("a:b", "c")).isNotEqualTo(UpdateReference.key("a", "b:c"));
  }
}
