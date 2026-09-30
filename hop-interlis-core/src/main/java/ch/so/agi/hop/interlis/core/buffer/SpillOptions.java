package ch.so.agi.hop.interlis.core.buffer;

import java.nio.file.Path;

public final class SpillOptions {
  private final long memoryBytes, maxDiskBytes;
  private final Path directory;
  private final SpillDiskBudget diskBudget;

  public SpillOptions(long memoryBytes, Path directory, long maxDiskBytes) {
    this(memoryBytes, directory, maxDiskBytes, new SpillDiskBudget(maxDiskBytes));
  }

  private SpillOptions(
      long memoryBytes, Path directory, long maxDiskBytes, SpillDiskBudget diskBudget) {
    if (memoryBytes <= 0 || maxDiskBytes < 0)
      throw new IllegalArgumentException("Invalid spill budget");
    this.memoryBytes = memoryBytes;
    this.directory = directory;
    this.maxDiskBytes = maxDiskBytes;
    this.diskBudget = diskBudget;
  }

  public long memoryBytes() {
    return memoryBytes;
  }

  public Path directory() {
    return directory;
  }

  public long maxDiskBytes() {
    return maxDiskBytes;
  }

  SpillDiskBudget diskBudget() {
    return diskBudget;
  }

  public static SpillOptions defaults() {
    return new SpillOptions(64L << 20, null, 0);
  }

  public SpillOptions divided(int parts) {
    if (parts <= 0) throw new IllegalArgumentException("Invalid buffer partition count");
    return new SpillOptions(Math.max(1, memoryBytes / parts), directory, maxDiskBytes, diskBudget);
  }
}
