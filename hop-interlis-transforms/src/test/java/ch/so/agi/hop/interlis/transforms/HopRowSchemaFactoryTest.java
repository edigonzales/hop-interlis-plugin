package ch.so.agi.hop.interlis.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.interlis.transforms.TestData;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.ProjectionOptions;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaBigNumber;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HopRowSchemaFactoryTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  private final HopRowSchemaFactory factory = new HopRowSchemaFactory();

  @Test
  void creates_stable_row_meta_for_geometry_class() throws Exception {
    IRowMeta rowMeta =
        factory.createRowMeta(
            new InterlisProjectionService()
                .project(
                    new InterlisModelRequest(
                        TestData.path("/data/HopIli_Geometry_V1_valid.xtf"),
                        List.of("HopIli_Geometry_V1"),
                        List.of(TestData.path("/models").toString())),
                    "HopIli_Geometry_V1.Data.TestObject",
                    ProjectionOptions.defaults())
                .plan());

    assertThat(rowMeta.size()).isEqualTo(10);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("_ili_tid");
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("_ili_bid");
    assertThat(rowMeta.getValueMeta(2).getName()).isEqualTo("Name");
    assertThat(rowMeta.getValueMeta(2)).isInstanceOf(ValueMetaString.class);
    assertThat(rowMeta.getValueMeta(3).getName()).isEqualTo("Center");
    assertThat(rowMeta.getValueMeta(3)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(rowMeta.getValueMeta(4).getName()).isEqualTo("Points");
    assertThat(rowMeta.getValueMeta(5).getName()).isEqualTo("Axis");
    assertThat(rowMeta.getValueMeta(6).getName()).isEqualTo("Axes");
    assertThat(rowMeta.getValueMeta(7).getName()).isEqualTo("Boundary");
    assertThat(rowMeta.getValueMeta(8).getName()).isEqualTo("Area");
    assertThat(rowMeta.getValueMeta(9).getName()).isEqualTo("Surfaces");
  }

  @Test
  void maps_primitive_value_kinds_to_hop_types() throws Exception {
    IRowMeta rowMeta =
        factory.createRowMeta(
            new InterlisProjectionService()
                .project(
                    new InterlisModelRequest(
                        null,
                        List.of("HopIli_Primitives_V1"),
                        List.of(TestData.path("/models").toString())),
                    "HopIli_Primitives_V1.Data.Primitive",
                    ProjectionOptions.defaults())
                .plan());

    // _ili_tid, _ili_bid, Text, Mtext, Label, Link, Flag, Count, Value, At, Ts, Kind, Opt
    assertThat(rowMeta.getValueMeta(2)).isInstanceOf(ValueMetaString.class);
    assertThat(rowMeta.getValueMeta(6)).isInstanceOf(ValueMetaBoolean.class);
    assertThat(rowMeta.getValueMeta(7)).isInstanceOf(ValueMetaInteger.class);
    assertThat(rowMeta.getValueMeta(8)).isInstanceOf(ValueMetaBigNumber.class);
    assertThat(rowMeta.getValueMeta(8).getPrecision()).isEqualTo(3);
    assertThat(rowMeta.getValueMeta(9).getName()).isEqualTo("At");
    assertThat(rowMeta.getValueMeta(11)).isInstanceOf(ValueMetaString.class); // enum
  }

  @Test
  void flattens_single_structure_and_projects_role() throws Exception {
    IRowMeta rowMeta =
        factory.createRowMeta(
            new InterlisProjectionService()
                .project(
                    new InterlisModelRequest(
                        null,
                        List.of("HopIli_Spike_V1"),
                        List.of(TestData.path("/models").toString())),
                    "HopIli_Spike_V1.Data.Building",
                    ProjectionOptions.defaults())
                .plan());

    assertThat(rowMeta.getFieldNames())
        .containsExactly(
            "_ili_tid", "_ili_bid", "Code", "Location", "Address_Street", "Address_Number",
            "Municipality_ref", "Note");
    assertThat(rowMeta.getValueMeta(3)).isInstanceOf(ValueMetaGeometry.class);
    assertThat(rowMeta.getValueMeta(6)).isInstanceOf(ValueMetaString.class);
  }
}
