package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.interlis.transforms.collect.*;
import ch.so.agi.hop.interlis.transforms.explode.*;
import ch.so.agi.hop.interlis.transforms.input.*;
import ch.so.agi.hop.interlis.transforms.objecttorow.*;
import ch.so.agi.hop.interlis.transforms.output.*;
import ch.so.agi.hop.interlis.transforms.rolejoin.*;
import ch.so.agi.hop.interlis.transforms.transferoutput.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;

class TransformMetadataOptionsTest {
  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  private <T extends ITransformMeta> T reload(T original) throws Exception {
    String xml = new TransformMeta("test", original).getXml();
    // The same annotation serializer used by Hop's .hpl loader, not only custom loadXml.
    return (T)
        XmlMetadataUtil.deSerializeFromXml(
            XmlHandler.loadXmlString(xml).getDocumentElement(),
            original.getClass(),
            new MemoryMetadataProvider());
  }

  @Test
  void collect_and_explode_options_survive_serialization() throws Exception {
    var meta = new InterlisStructureCollectMeta();
    meta.setDefault();
    meta.setSelectedChildFields(java.util.List.of("Name"));
    meta.setBufferMemoryMiB(3);
    meta.setMaxSpillMiB(40);
    meta.setSpillDirectory("${TEMP_DIR}");
    var modern = reload(meta);
    assertThat(modern.getSelectedChildFields()).containsExactly("Name");
    assertThat(modern.getBufferMemoryMiB()).isEqualTo(3);
    assertThat(modern.getMaxSpillMiB()).isEqualTo(40);
    var explode = new InterlisStructureExplodeMeta();
    explode.setKeepChildSourceObject(false);
    assertThat(reload(explode).isKeepChildSourceObject()).isFalse();
  }

  @Test
  void omitted_options_use_current_structure_and_writer_defaults() throws Exception {
    var node = XmlHandler.loadXmlString("<transform/>").getDocumentElement();
    var provider = new MemoryMetadataProvider();
    var collect =
        XmlMetadataUtil.deSerializeFromXml(node, InterlisStructureCollectMeta.class, provider);
    assertThat(collect.getCollectMode())
        .isEqualTo(InterlisStructureCollectMeta.CollectMode.PRESERVE);
    assertThat(collect.getParentBidField()).isEqualTo("_ili_bid");
    assertThat(collect.getChildParentBidField()).isEqualTo("_ili_parent_bid");
    assertThat(collect.isStrictOrdering()).isTrue();
    assertThat(collect.isFailOnDuplicateIndex()).isTrue();
    assertThat(collect.isFailOnChildWithoutParent()).isTrue();

    var explode =
        XmlMetadataUtil.deSerializeFromXml(node, InterlisStructureExplodeMeta.class, provider);
    assertThat(explode.isEmitParentBid()).isTrue();
    assertThat(explode.isKeepChildSourceObject()).isTrue();
    assertThat(explode.isEmitUpdateReference()).isTrue();
    var output = XmlMetadataUtil.deSerializeFromXml(node, InterlisOutputMeta.class, provider);
    assertThat(output.isValidateBeforePublish()).isTrue();
    var update =
        XmlMetadataUtil.deSerializeFromXml(
            node, ch.so.agi.hop.interlis.transforms.update.InterlisUpdateMeta.class, provider);
    assertThat(update.isValidateBeforePublish()).isTrue();
  }

  @Test
  void buffer_and_output_options_survive_serialization() throws Exception {
    var input = new InterlisInputMeta();
    input.setDefault();
    input.setBufferMemoryMiB(2);
    input.setMaxSpillMiB(50);
    assertThat(reload(input).getBufferMemoryMiB()).isEqualTo(2);
    var project = new InterlisObjectToRowMeta();
    project.setDefault();
    project.setBufferMemoryMiB(4);
    assertThat(reload(project).getBufferMemoryMiB()).isEqualTo(4);
    var join = new InterlisRoleJoinMeta();
    join.setDefault();
    join.setMaxLookupRows(0);
    join.setBufferMemoryMiB(5);
    assertThat(reload(join).getMaxLookupRows()).isZero();
    var output = new InterlisOutputMeta();
    output.setDefault();
    output.setValidationConfigFile("config.ini");
    output.setValidateBeforePublish(false);
    var restoredOutput = reload(output);
    assertThat(restoredOutput.isValidateBeforePublish()).isFalse();
    assertThat(restoredOutput.getValidationConfigFile()).isEqualTo("config.ini");
    var transfer = new InterlisTransferOutputMeta();
    transfer.setDefault();
    transfer.setValidateBeforePublish(true);
    assertThat(reload(transfer).isValidateBeforePublish()).isTrue();
  }
}
