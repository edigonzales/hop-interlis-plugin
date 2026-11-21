package ch.so.agi.hop.interlis.transforms.output;

import ch.so.agi.hop.interlis.transforms.mapping.ui.MappedSinkDialog;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;

public class InterlisOutputDialog extends MappedSinkDialog {
  public InterlisOutputDialog(
      Shell parent, IVariables vars, InterlisOutputMeta meta, PipelineMeta pipeline) {
    super(parent, vars, meta, pipeline);
  }
}
