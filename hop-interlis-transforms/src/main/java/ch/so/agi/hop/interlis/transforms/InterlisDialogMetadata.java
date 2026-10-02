package ch.so.agi.hop.interlis.transforms;

import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transform.BaseTransformMeta;

/** Commit a private dialog draft only after OK, using the same persistence path as Hop. */
public final class InterlisDialogMetadata {
  private InterlisDialogMetadata() {}

  public static void commit(
      BaseTransformMeta<?, ?> target, BaseTransformMeta<?, ?> draft, IHopMetadataProvider provider)
      throws Exception {
    target.loadXml(
        XmlHandler.loadXmlString("<transform>" + draft.getXml() + "</transform>")
            .getDocumentElement(),
        provider);
    target.setChanged();
  }
}
