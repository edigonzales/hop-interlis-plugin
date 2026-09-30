package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.transforms.collect.*;
import ch.so.agi.hop.interlis.transforms.explode.*;
import ch.so.agi.hop.interlis.transforms.input.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.rolejoin.*;
import ch.so.agi.hop.interlis.transforms.transferoutput.*;
import java.io.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;

class NewOptionsCompatibilityTest {
  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  <T> T reload(T original, boolean legacy) throws Exception {
    var meta = (ITransformMeta) original;
    String xml = new TransformMeta("test", meta).getXml();
    if (legacy)
      xml =
          xml.replaceAll(
              "(?s)<(collect_mode|parent_bid_field|child_parent_bid_field|keep_child_source_object|bufferMemoryMiB|spillDirectory|maxSpillMiB|validateBeforePublish|validationConfigFile)>.*?</\\1>",
              "");
    var restored = original.getClass().getConstructor().newInstance();
    // The same annotation serializer used by Hop's .hpl loader, not only custom loadXml.
    restored =
        XmlMetadataUtil.deSerializeFromXml(
            XmlHandler.loadXmlString(xml).getDocumentElement(),
            original.getClass(),
            new MemoryMetadataProvider());
    return (T) restored;
  }

  @Test
  void new_collect_defaults_and_old_hpl_rebuild_are_distinct() throws Exception {
    var meta = new InterlisStructureCollectMeta();
    meta.setDefault();
    meta.setSelectedChildFields(java.util.List.of("Name"));
    meta.setBufferMemoryMiB(3);
    meta.setMaxSpillMiB(40);
    meta.setSpillDirectory("${TEMP_DIR}");
    var modern = reload(meta, false);
    assertThat(modern.getSelectedChildFields()).containsExactly("Name");
    assertThat(modern.getCollectMode())
        .isEqualTo(InterlisStructureCollectMeta.CollectMode.PRESERVE);
    assertThat(modern.getParentBidField()).isEqualTo("_ili_bid");
    assertThat(modern.getChildParentBidField()).isEqualTo("_ili_parent_bid");
    assertThat(modern.getBufferMemoryMiB()).isEqualTo(3);
    assertThat(modern.getMaxSpillMiB()).isEqualTo(40);
    var old = reload(meta, true);
    assertThat(old.getCollectMode()).isEqualTo(InterlisStructureCollectMeta.CollectMode.REBUILD);
    assertThat(old.getParentBidField()).isNullOrEmpty();
    assertThat(old.getChildParentBidField()).isNullOrEmpty();
    assertThat(old.getBufferMemoryMiB()).isEqualTo(64);
    var explode = new InterlisStructureExplodeMeta();
    explode.setDefault();
    assertThat(reload(explode, false).isKeepChildSourceObject()).isTrue();
    assertThat(reload(explode, true).isKeepChildSourceObject()).isFalse();
  }

  @Test
  void buffer_and_output_options_roundtrip_and_missing_options_keep_compatible_defaults()
      throws Exception {
    var input = new InterlisInputMeta();
    input.setDefault();
    input.setBufferMemoryMiB(2);
    input.setMaxSpillMiB(50);
    assertThat(reload(input, false).getBufferMemoryMiB()).isEqualTo(2);
    assertThat(reload(input, true).getBufferMemoryMiB()).isEqualTo(64);
    var project = new InterlisObjectToRowMeta();
    project.setDefault();
    project.setBufferMemoryMiB(4);
    assertThat(reload(project, false).getBufferMemoryMiB()).isEqualTo(4);
    assertThat(reload(project, true).getBufferMemoryMiB()).isEqualTo(64);
    var join = new InterlisRoleJoinMeta();
    join.setDefault();
    join.setMaxLookupRows(0);
    join.setBufferMemoryMiB(5);
    assertThat(reload(join, false).getMaxLookupRows()).isZero();
    assertThat(reload(join, true).getBufferMemoryMiB()).isEqualTo(64);
    var output = new InterlisOutputMeta();
    output.setDefault();
    output.setValidateBeforePublish(true);
    output.setValidationConfigFile("config.ini");
    assertThat(reload(output, false).isValidateBeforePublish()).isTrue();
    assertThat(reload(output, true).isValidateBeforePublish()).isFalse();
    var transfer = new InterlisTransferOutputMeta();
    transfer.setDefault();
    transfer.setValidateBeforePublish(true);
    assertThat(reload(transfer, false).isValidateBeforePublish()).isTrue();
    assertThat(reload(transfer, true).isValidateBeforePublish()).isFalse();
  }
}
