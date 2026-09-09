package ch.so.agi.hop.interlis.transforms.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;

/** Resolves an incoming envelope schema once, preserving the original row for append output. */
public final class InterlisEnvelopeBindings {
  private final InterlisRowBindings fields;
  private final int size;
  private final boolean eventMode;

  private InterlisEnvelopeBindings(InterlisRowBindings fields, int size, boolean eventMode) {
    this.fields = fields;
    this.size = size;
    this.eventMode = eventMode;
  }

  public static InterlisEnvelopeBindings bind(IRowMeta input, String objectField, boolean eventMode)
      throws HopException {
    var canonical = InterlisEnvelopeSchemaFactory.createRowMeta();
    var fields = new java.util.ArrayList<InterlisFieldBinding>();
    for (int i = 0; i < canonical.size(); i++) {
      String name =
          i == InterlisEnvelopeRowLayout.OBJECT_INDEX
              ? objectField
              : canonical.getValueMeta(i).getName();
      fields.add(
          InterlisFieldBinding.bind(
              input,
              "INTERLIS envelope input",
              name,
              canonical.getValueMeta(i).getName(),
              i,
              canonical.getValueMeta(i),
              i == InterlisEnvelopeRowLayout.OBJECT_INDEX
                  || (eventMode && i == InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX)));
    }
    return new InterlisEnvelopeBindings(
        new InterlisRowBindings(fields), canonical.size(), eventMode);
  }

  public InterlisObjectEnvelope fromRow(Object[] row) throws HopException {
    Object[] values = new Object[size];
    try {
      values = fields.values(row);
      if (eventMode && (values[0] == null || values[0].toString().isBlank()))
        throw new IllegalArgumentException("Event mode requires _ili_event_type on every row");
      if (values[InterlisEnvelopeRowLayout.OBJECT_INDEX] instanceof IomObject object) {
        if (values[InterlisEnvelopeRowLayout.CLASS_INDEX] == null)
          values[InterlisEnvelopeRowLayout.CLASS_INDEX] = object.getobjecttag();
        if (values[InterlisEnvelopeRowLayout.TID_INDEX] == null)
          values[InterlisEnvelopeRowLayout.TID_INDEX] = object.getobjectoid();
        if (values[InterlisEnvelopeRowLayout.OPERATION_INDEX] == null)
          values[InterlisEnvelopeRowLayout.OPERATION_INDEX] =
              InterlisObjectOperation.fromIom(object.getobjectoperation()).name();
      }
      return InterlisEnvelopeRowLayout.fromRow(values);
    } catch (RuntimeException e) {
      throw new HopException(
          "Invalid envelope row (TID "
              + values[InterlisEnvelopeRowLayout.TID_INDEX]
              + ", basket "
              + values[InterlisEnvelopeRowLayout.BID_INDEX]
              + "): "
              + e.getMessage(),
          e);
    }
  }
}
