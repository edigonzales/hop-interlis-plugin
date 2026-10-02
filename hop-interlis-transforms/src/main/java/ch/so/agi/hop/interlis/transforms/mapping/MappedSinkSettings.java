package ch.so.agi.hop.interlis.transforms.mapping;

import ch.so.agi.hop.interlis.core.buffer.SpillOptions;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.variables.IVariables;

public interface MappedSinkSettings {
  String getFileName();

  String getModelNames();

  String getModelDirectories();

  boolean isOverwrite();

  boolean isValidateBeforePublish();

  String getValidationConfigFile();

  long getBufferMemoryMiB();

  long getMaxSpillMiB();

  String getSpillDirectory();

  List<InterlisMappedInput> getInputs();

  void setFileName(String value);

  void setModelNames(String value);

  void setModelDirectories(String value);

  void setOverwrite(boolean value);

  void setValidateBeforePublish(boolean value);

  void setValidationConfigFile(String value);

  void setBufferMemoryMiB(long value);

  void setMaxSpillMiB(long value);

  void setSpillDirectory(String value);

  void setInputs(List<InterlisMappedInput> value);

  default SpillOptions spillOptions(IVariables vars) {
    String dir = InterlisFieldBinding.resolve(vars, getSpillDirectory());
    return new SpillOptions(
        Math.multiplyExact(getBufferMemoryMiB(), 1L << 20),
        dir.isBlank() ? null : Path.of(dir),
        Math.multiplyExact(getMaxSpillMiB(), 1L << 20));
  }
}
