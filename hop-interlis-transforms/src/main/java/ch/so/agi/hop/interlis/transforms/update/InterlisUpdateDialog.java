package ch.so.agi.hop.interlis.transforms.update;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;

public class InterlisUpdateDialog
    extends ch.so.agi.hop.interlis.transforms.mapping.ui.MappedSinkDialog {
  public InterlisUpdateDialog(
      Shell parent, IVariables vars, InterlisUpdateMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
  }
}
