package ch.so.agi.hop.interlis.transforms;

import ch.so.agi.hop.interlis.core.io.InterlisEnvelopeRowLayout;
import ch.so.agi.hop.interlis.transforms.value.ValueMetaInterlisObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;

/**
 * Builds the canonical Hop row schema of the INTERLIS envelope stream.
 *
 * <p>The schema is defined by {@link InterlisEnvelopeRowLayout}; all generic transforms share
 * this factory so the layout cannot drift between them.
 */
public final class InterlisEnvelopeSchemaFactory {

  private InterlisEnvelopeSchemaFactory() {}

  /** The constant envelope row schema. */
  public static RowMeta createRowMeta() throws HopException {
    InterlisRuntimeSupport.initialize();
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.EVENT_TYPE));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.MODEL));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.TOPIC));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.BID));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.CLASS));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.TID));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.OPERATION));
    rowMeta.addValueMeta(new ValueMetaInterlisObject(InterlisEnvelopeRowLayout.OBJECT));
    rowMeta.addValueMeta(new ValueMetaInteger(InterlisEnvelopeRowLayout.LINE));
    rowMeta.addValueMeta(new ValueMetaInteger(InterlisEnvelopeRowLayout.COLUMN));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.BASKET_CONSISTENCY));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.BASKET_KIND));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.BASKET_START_STATE));
    rowMeta.addValueMeta(new ValueMetaString(InterlisEnvelopeRowLayout.BASKET_END_STATE));
    return rowMeta;
  }

  /** The constant validation error row schema. */
  public static RowMeta validationRowMeta() throws HopException {
    InterlisRuntimeSupport.initialize();
    RowMeta rowMeta = new RowMeta();
    for (String fieldName : ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout.FIELD_NAMES) {
      if (ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout.LINE.equals(fieldName)
          || ch.so.agi.hop.interlis.core.io.InterlisValidationRowLayout.COLUMN.equals(fieldName)) {
        rowMeta.addValueMeta(new ValueMetaInteger(fieldName));
      } else {
        rowMeta.addValueMeta(new ValueMetaString(fieldName));
      }
    }
    return rowMeta;
  }
}
