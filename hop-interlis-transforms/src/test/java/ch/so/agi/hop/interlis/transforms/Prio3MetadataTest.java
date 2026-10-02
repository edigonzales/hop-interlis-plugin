package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.transforms.explode.InterlisStructureExplodeMeta;
import ch.so.agi.hop.interlis.transforms.input.InterlisInputMeta;
import ch.so.agi.hop.interlis.transforms.mapping.*;
import ch.so.agi.hop.interlis.transforms.output.InterlisOutputMeta;
import ch.so.agi.hop.interlis.transforms.update.InterlisUpdateMeta;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;

class Prio3MetadataTest {
  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  static <T> T reload(T meta, String remove) throws Exception {
    String xml = new TransformMeta("test", (ITransformMeta) meta).getXml();
    if (!remove.isBlank()) xml = xml.replaceAll("(?s)<(" + remove + ")>.*?</\\1>", "");
    return (T)
        XmlMetadataUtil.deSerializeFromXml(
            XmlHandler.loadXmlString(xml).getDocumentElement(),
            meta.getClass(),
            new MemoryMetadataProvider());
  }

  @Test
  void nested_mappings_and_output_mode_survive_hop_serialization() throws Exception {
    var meta = new InterlisOutputMeta();
    meta.setDefault();
    var mapping = new InterlisMappedInput();
    mapping.setTransformName("buildings");
    mapping.setClassName("Model.Data.Building");
    mapping.setFields(List.of(new InterlisFieldAssignment("label", "Address.Name")));
    meta.setInputs(List.of(mapping));
    var copy = reload(meta, "");
    assertThat(copy.getMode()).isEqualTo(InterlisOutputMeta.Mode.MAPPED_INPUTS);
    assertThat(copy.isValidateBeforePublish()).isTrue();
    assertThat(copy.getInputs()).hasSize(1);
    assertThat(copy.getInputs().getFirst().getFields().getFirst().getTargetPath())
        .isEqualTo("Address.Name");
    assertThat(copy.getInputs().getFirst().getFields().getFirst().getSourceField())
        .isEqualTo("label");
    assertThat(copy.getTransformIOMeta().isOutputProducer()).isFalse();
    assertThat(reload(meta, "mode|validateBeforePublish").getMode())
        .isEqualTo(InterlisOutputMeta.Mode.SINGLE_SCHEMA);
    assertThat(reload(meta, "mode|validateBeforePublish").isValidateBeforePublish()).isFalse();
    var xml =
        XmlHandler.loadXmlString("<transform><className>Old.Data.Class</className></transform>")
            .getDocumentElement();
    meta.loadXml(xml, new MemoryMetadataProvider());
    assertThat(meta.getMode()).isEqualTo(InterlisOutputMeta.Mode.SINGLE_SCHEMA);
  }

  @Test
  void update_and_explode_options_survive_and_old_explode_layout_stays_unchanged()
      throws Exception {
    var update = new InterlisUpdateMeta();
    update.setOriginalFile("${ORIGINAL}");
    var input = new InterlisMappedInput();
    input.setStructurePath("Holder.Children");
    input.setFields(List.of(new InterlisFieldAssignment("newName", "Name")));
    update.setInputs(List.of(input));
    var copy = reload(update, "");
    assertThat(copy.getOriginalFile()).isEqualTo("${ORIGINAL}");
    assertThat(copy.getInputs().getFirst().getStructurePath()).isEqualTo("Holder.Children");
    var explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    assertThat(reload(explode, "").isEmitUpdateReference()).isTrue();
    assertThat(reload(explode, "emit_update_reference").isEmitUpdateReference()).isFalse();
  }

  @Test
  void field_selection_distinguishes_all_from_technical_only() throws Exception {
    var meta = new InterlisInputMeta();
    meta.setDefault();
    meta.setFileName(TestData.path("/data/HopIli_Geometry_V1_valid.xtf").toString());
    meta.setModelDirectories(TestData.path("/models").toString());
    meta.setClassName("HopIli_Geometry_V1.Data.TestObject");
    meta.setKeepSourceObject(true);
    meta.setSelectFields(true);
    var fields = new RowMeta();
    meta.getFields(fields, "read", null, null, new Variables(), null);
    assertThat(fields.getFieldNames())
        .containsExactly("_ili_tid", "_ili_bid", "_ili_source_object");
    assertThat(reload(meta, "").isSelectFields()).isTrue();
    meta.setSelectFields(false);
    meta.setSelectedFields(List.of("Name"));
    fields.clear();
    meta.getFields(fields, "read", null, null, new Variables(), null);
    assertThat(fields.getFieldNames()).contains("Axis", "Name");
  }

  @Test
  void rename_keeps_mapping_and_disconnection_does_not_silently_delete_it() {
    var source = new TransformMeta("before", new InterlisInputMeta());
    var config = new InterlisMappedInput();
    config.setTransformName("before");
    var meta = new InterlisUpdateMeta();
    meta.setInputs(List.of(config));
    meta.searchInfoAndTargetTransforms(List.of(source));
    source.setName("after");
    meta.searchInfoAndTargetTransforms(List.of(source));
    assertThat(meta.getInputs().getFirst().getTransformName()).isEqualTo("after");
    meta.searchInfoAndTargetTransforms(List.of());
    assertThat(meta.getInputs()).hasSize(1);
    assertThat(meta.getTransformIOMeta().getInfoStreams().getFirst().getTransformMeta()).isNull();
  }

  @Test
  void draft_mapping_mutations_do_not_touch_the_original() {
    var original = new InterlisOutputMeta();
    original.setDefault();
    var input = new InterlisMappedInput();
    input.setFields(List.of(new InterlisFieldAssignment("Name", "Name")));
    original.setInputs(List.of(input));
    var draft = (InterlisOutputMeta) original.clone();
    draft.getInputs().getFirst().getFields().getFirst().setSourceField("different");
    draft.getInputs().clear();
    assertThat(original.getInputs()).hasSize(1);
    assertThat(original.getInputs().getFirst().getFields().getFirst().getSourceField())
        .isEqualTo("Name");
  }
}
