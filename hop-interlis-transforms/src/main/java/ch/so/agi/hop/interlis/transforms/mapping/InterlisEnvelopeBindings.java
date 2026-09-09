package ch.so.agi.hop.interlis.transforms.mapping;

import ch.interlis.iom.IomObject;
import ch.so.agi.hop.interlis.core.io.*;
import ch.so.agi.hop.interlis.transforms.InterlisEnvelopeSchemaFactory;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;

/** Resolves an incoming envelope schema once, preserving the original row for append output. */
public final class InterlisEnvelopeBindings {
  private final int[] indexes;
  private final boolean eventMode;

  private InterlisEnvelopeBindings(int[] indexes, boolean eventMode) {
    this.indexes = indexes;
    this.eventMode = eventMode;
  }

  public static InterlisEnvelopeBindings bind(IRowMeta input, String objectField, boolean eventMode)
      throws HopException {
    var canonical = InterlisEnvelopeSchemaFactory.createRowMeta();
    int[] indexes = new int[canonical.size()];
    for (int i = 0; i < indexes.length; i++) {
      String name =
          i == InterlisEnvelopeRowLayout.OBJECT_INDEX
              ? objectField
              : canonical.getValueMeta(i).getName();
      int matches = 0;
      for (var field : input.getValueMetaList())
        if (field.getName().equalsIgnoreCase(name)) matches++;
      if (matches > 1) throw new HopException("Ambiguous envelope field <" + name + ">");
      indexes[i] = input.indexOfValue(name);
      boolean required =
          i == InterlisEnvelopeRowLayout.OBJECT_INDEX
              || (eventMode && i == InterlisEnvelopeRowLayout.EVENT_TYPE_INDEX);
      if (indexes[i] < 0) {
        if (required)
          throw new HopException("Envelope field <" + name + "> not found in the input");
      } else if (input.getValueMeta(indexes[i]).getType() != canonical.getValueMeta(i).getType()) {
        throw new HopException(
            "Envelope field <"
                + name
                + "> must have type "
                + canonical.getValueMeta(i).getTypeDesc());
      }
    }
    return new InterlisEnvelopeBindings(indexes, eventMode);
  }

  public InterlisObjectEnvelope fromRow(Object[] row) throws HopException {
    Object[] values = new Object[indexes.length];
    try {
      for (int i = 0; i < indexes.length; i++) if (indexes[i] >= 0) values[i] = row[indexes[i]];
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
