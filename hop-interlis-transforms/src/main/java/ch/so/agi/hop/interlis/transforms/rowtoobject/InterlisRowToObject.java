package ch.so.agi.hop.interlis.transforms.rowtoobject;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.core.io.InterlisObjectOperation;
import ch.so.agi.hop.interlis.core.mapping.InterlisModelRequest;
import ch.so.agi.hop.interlis.core.mapping.InterlisProjectionService;
import ch.so.agi.hop.interlis.core.mapping.RowToIomMapper;
import ch.so.agi.hop.interlis.core.mapping.RowWriteOptions;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import ch.so.agi.hop.interlis.transforms.InterlisModelSourceSupport;
import ch.so.agi.hop.interlis.transforms.InterlisRuntimeSupport;
import ch.so.agi.hop.interlis.transforms.mapping.InterlisRowBindings;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * INTERLIS Row to Object: maps typed class rows back to canonical envelope rows.
 *
 * <p>The output schema is the constant envelope schema; the typed values are mapped to the IOM
 * object via {@link RowToIomMapper} (including regenerated association link objects, which become
 * additional OBJECT envelope rows). The result can be merged with other classes' envelope rows and
 * written by INTERLIS Transfer Output.
 */
public class InterlisRowToObject
    extends BaseTransform<InterlisRowToObjectMeta, InterlisRowToObjectData> {

  public InterlisRowToObject(
      TransformMeta transformMeta,
      InterlisRowToObjectMeta meta,
      InterlisRowToObjectData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (data.initialized && data.pendingRows != null && data.pendingRows.hasNext()) {
      putRow(data.outputRowMeta, data.pendingRows.next());
      return true;
    }

    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      if (isBasic()) {
        logBasic("Finished mapping " + data.rowsMapped + " rows of " + meta.getClassName());
      }
      return false;
    }

    if (!data.initialized) {
      doInitialize();
    }

    try {
      Object[] values = InterlisRowBindings.values(row, data.inputIndexes);
      IomObject carrier =
          data.sourceObjectIndex < 0 ? null : (IomObject) row[data.sourceObjectIndex];
      Object operationValue = data.operationIndex < 0 ? null : row[data.operationIndex];
      InterlisObjectOperation operation;
      try {
        operation =
            operationValue == null || operationValue.toString().isBlank()
                ? (carrier == null
                    ? InterlisObjectOperation.NONE
                    : InterlisObjectOperation.fromIom(carrier.getobjectoperation()))
                : InterlisObjectOperation.valueOf(operationValue.toString());
      } catch (IllegalArgumentException e) {
        throw new HopException("Invalid operation field value <" + operationValue + ">", e);
      }
      var writeOptions = new RowWriteOptions(true, null, operation);
      RowToIomMapper.InterlisWriteResult result =
          data.sourceObjectIndex < 0
              ? data.mapper.mapAll(values, data.plan, writeOptions)
              : data.mapper.mapAll(carrier, values, data.plan, writeOptions);

      String basketId =
          data.basketIdFieldIndex >= 0 && row[data.basketIdFieldIndex] != null
              ? row[data.basketIdFieldIndex].toString().trim()
              : null;
      if (basketId != null && basketId.isEmpty()) {
        basketId = null;
      }
      String topicName = data.plan.root().topicScopedName();

      Object[] primary =
          InterlisEnvelopeRowLayout.objectRow(result.object(), basketId, topicName, operation);
      var basket =
          new ch.so.agi.hop.interlis.core.io.InterlisBasketMetadata(
              metadataValue(row, 0),
              metadataValue(row, 1),
              metadataValue(row, 2),
              metadataValue(row, 3));
      primary[InterlisEnvelopeRowLayout.BASKET_CONSISTENCY_INDEX] = basket.consistency();
      primary[InterlisEnvelopeRowLayout.BASKET_KIND_INDEX] = basket.kind();
      primary[InterlisEnvelopeRowLayout.BASKET_START_STATE_INDEX] = basket.startState();
      primary[InterlisEnvelopeRowLayout.BASKET_END_STATE_INDEX] = basket.endState();
      if (result.additionalObjects().isEmpty()) {
        data.rowsMapped++;
        putRow(data.outputRowMeta, primary);
        return true;
      }

      List<Object[]> envelopeRows = new java.util.ArrayList<>();
      envelopeRows.add(primary);
      for (IomObject link : result.additionalObjects()) {
        var linkRow =
            InterlisEnvelopeRowLayout.objectRow(
                link, basketId, topicName, InterlisObjectOperation.NONE);
        System.arraycopy(
            primary,
            InterlisEnvelopeRowLayout.BASKET_CONSISTENCY_INDEX,
            linkRow,
            InterlisEnvelopeRowLayout.BASKET_CONSISTENCY_INDEX,
            4);
        envelopeRows.add(linkRow);
      }
      data.pendingRows = envelopeRows.iterator();
      data.rowsMapped++;
      putRow(data.outputRowMeta, data.pendingRows.next());
      return true;
    } catch (Exception e) {
      throw new HopException(
          "Failed to map row to INTERLIS object for class "
              + meta.getClassName()
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private void doInitialize() throws HopException {
    InterlisRuntimeSupport.initialize();

    try {
      data.projection =
          new InterlisProjectionService()
              .project(
                  new InterlisModelRequest(null, resolveModelNames(), resolveModelDirectories()),
                  resolve(meta.getClassName()),
                  meta.projectionOptions());
      data.plan = data.projection.plan();
      data.mapper = new RowToIomMapper();
      var bindings = InterlisRowToObjectBindings.bind(getInputRowMeta(), data.plan, meta, this);
      data.inputIndexes = bindings.values();
      data.basketIdFieldIndex = bindings.basket();
      data.sourceObjectIndex = bindings.source();
      data.operationIndex = bindings.operation();
      data.basketMetadataIndexes = bindings.basketMetadata();
      data.outputRowMeta = InterlisEnvelopeSchemaFactory.createRowMeta();
      if (isBasic()) {
        logBasic("Mapping rows of " + data.plan.root().scopedName() + " to envelope rows");
      }
      data.initialized = true;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Failed to initialize INTERLIS Row to Object: " + e.getMessage(), e);
    }
  }

  private String metadataValue(Object[] row, int field) {
    int index = data.basketMetadataIndexes[field];
    return index < 0 || row[index] == null ? null : row[index].toString();
  }

  private List<String> resolveModelNames() {
    String resolved = resolve(meta.getModelNames());
    return InterlisModelSourceSupport.parseModelNames(resolved);
  }

  private List<String> resolveModelDirectories() {
    String resolved = resolve(meta.getModelDirectories());
    if (resolved.isBlank()) {
      return List.of();
    }
    return InterlisModelSourceSupport.parseModelDirectories(resolved);
  }
}
