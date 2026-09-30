package ch.so.agi.hop.interlis.core.io;

import java.nio.channels.FileChannel;
import java.nio.file.*;

/** One file's prepare/publish lifecycle; the writer never opens the final target. */
public final class PreparedXtfOutput implements AutoCloseable {
  private final Path target, temporary;
  private final boolean overwrite;
  private boolean ready, published, aborted;

  public PreparedXtfOutput(Path target, boolean overwrite) throws java.io.IOException {
    this.target = target.toAbsolutePath().normalize();
    this.overwrite = overwrite;
    if (!overwrite && Files.exists(this.target))
      throw new FileAlreadyExistsException(this.target.toString());
    Files.createDirectories(this.target.getParent());
    temporary =
        Files.createTempFile(
            this.target.getParent(), "." + this.target.getFileName() + "-", ".pending.xtf");
  }

  public Path target() {
    return target;
  }

  public Path temporary() {
    return temporary;
  }

  public boolean ready() {
    return ready && !aborted;
  }

  public boolean published() {
    return published;
  }

  public void prepare() throws java.io.IOException {
    if (aborted || published) throw new IllegalStateException("Output has already completed");
    try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    ready = true;
  }

  public void publish() throws java.io.IOException {
    if (published) return;
    if (!ready()) throw new IllegalStateException("Output was not prepared: " + target);
    if (overwrite)
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    else {
      // Creating a hard link is an atomic CREATE_NEW operation; ATOMIC_MOVE without
      // REPLACE_EXISTING is allowed to overwrite on Unix and would race other writers.
      Files.createLink(target, temporary);
      published = true;
      Files.delete(temporary);
    }
    published = true;
  }

  @Override
  public void close() throws java.io.IOException {
    if (!published) aborted = true;
    Files.deleteIfExists(temporary);
  }
}
