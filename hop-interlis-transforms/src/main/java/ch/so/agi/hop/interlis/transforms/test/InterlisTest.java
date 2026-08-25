package ch.so.agi.hop.interlis.transforms.test;

import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Minimal experimental inputless transform (Phase 0 spike).
 *
 * <p>Emits three rows with a string TID/BID/name and a real Hop {@code Geometry} value, proving
 * that the shared geometry value type from {@code hop-geometry-type-plugin} can be produced inside
 * a transform registered in the {@code sogeo-geometry} classloader group.
 */
public class InterlisTest extends BaseTransform<InterlisTestMeta, InterlisTestData> {

  public InterlisTest(
      TransformMeta transformMeta,
      InterlisTestMeta meta,
      InterlisTestData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (first) {
      first = false;
      data.rowIndex = 0;

      // Merge the sogeo-geometry classloader group so the geometry classes resolve.
      InterlisRuntimeSupport.initialize();

      RowMeta outputRowMeta = new RowMeta();
      meta.getFields(outputRowMeta, getTransformName(), null, null, this, metadataProvider);

      GeometryFactory geometryFactory = new GeometryFactory();
      String prefix = meta.getPrefix() == null ? "interlis" : meta.getPrefix();

      putRow(
          outputRowMeta,
          new Object[] {
            "o1",
            "b1",
            prefix + "-a",
            geometryFactory.createPoint(new Coordinate(2600000, 1200000))
          });
      putRow(
          outputRowMeta,
          new Object[] {
            "o2",
            "b1",
            prefix + "-b",
            geometryFactory.createPoint(new Coordinate(2600100, 1200100))
          });
      putRow(
          outputRowMeta,
          new Object[] {"o3", "b2", prefix + "-c", null});
    }

    setOutputDone();
    return false;
  }
}
