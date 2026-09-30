package ch.so.agi.hop.interlis.transforms.buffer;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.so.agi.hop.interlis.core.buffer.*;
import ch.so.agi.hop.interlis.core.geometry.InterlisGeometryMapper;
import ch.so.agi.hop.interlis.core.model.*;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import com.atolcd.hop.gis.geometry.curve.*;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class HopSpillCodecTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void forced_spill_keeps_arc_xyz_srid_reference_metadata_and_nested_carrier() throws Exception {
    var descriptor =
        new InterlisAttributeDescriptor(
            "Axis",
            "M.T.C.Axis",
            new InterlisCardinality(0, 1),
            false,
            InterlisValueKind.GEOMETRY,
            "POLYLINE",
            false,
            InterlisGeometryKind.POLYLINE,
            2,
            true,
            null,
            false,
            -1,
            -1,
            InterlisGeometryEncoding.NATIVE);
    var line = new Iom_jObject("POLYLINE", null);
    var sequence = line.addattrobj("sequence", "SEGMENTS");
    var coord = sequence.addattrobj("segment", "COORD");
    coord.setattrvalue("C1", "0");
    coord.setattrvalue("C2", "0");
    var arc = sequence.addattrobj("segment", "ARC");
    arc.setattrvalue("A1", "1");
    arc.setattrvalue("A2", "1");
    arc.setattrvalue("C1", "2");
    arc.setattrvalue("C2", "0");
    Geometry curve =
        new InterlisGeometryMapper().toHopGeometry(line, InterlisGeometryKind.POLYLINE, 2);
    curve.setSRID(2056);
    Geometry xyz =
        new GeometryFactory(new PrecisionModel(), 2056).createPoint(new Coordinate(1, 2, 3));
    var carrier = new Iom_jObject("M.T.Subtype", "tid");
    carrier.setobjectoperation(
        ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.UPDATE.toIom());
    var child = (Iom_jObject) carrier.addattrobj("Children", "M.T.Child");
    child.setattrvalue("Hidden", "keep");
    child.addattrvalue("Tags", "one");
    child.addattrvalue("Tags", "two");
    var ref = child.addattrobj("Ref", "REF");
    ref.setobjectrefoid("target");
    ref.setobjectrefbid("external");
    ref.setobjectreforderpos(4);
    var meta = new RowMeta();
    meta.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("arc"));
    meta.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("xyz"));
    meta.addValueMeta(new ValueMetaInterlisObject("carrier"));
    for (long memory : new long[] {1, 1024 * 1024})
      try (var store =
          new SpillStore<Object[]>(new HopRowCodec(meta), new SpillOptions(memory, temp, 0))) {
        store.append(new Object[] {curve, xyz, carrier});
        var row = store.poll();
        assertThat(row[0]).isInstanceOf(CompoundCurve.class);
        assertThat(((Geometry) row[0]).getSRID()).isEqualTo(2056);
        assertThat(((Geometry) row[1]).getCoordinate().getZ()).isEqualTo(3);
        assertThat(((Geometry) row[1]).getSRID()).isEqualTo(2056);
        var restored = (IomObject) row[2];
        assertThat(restored.getobjectoperation())
            .isEqualTo(ch.so.agi.hop.interlis.core.io.InterlisObjectOperation.UPDATE.toIom());
        assertThat(restored.getattrobj("Children", 0).getattrvaluecount("Tags")).isEqualTo(2);
        assertThat(restored.getattrobj("Children", 0).getattrobj("Ref", 0).getobjectrefbid())
            .isEqualTo("external");
        assertThat(restored.getattrobj("Children", 0).getattrobj("Ref", 0).getobjectreforderpos())
            .isEqualTo(4);
      }
  }
}
