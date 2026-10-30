package net.tfminecraft.vfbuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One shared data directory per JVM for tests that save stations. */
public final class DatabaseFixture {
  private static final Path ROOT;

  static {
    try {
      ROOT = Files.createTempDirectory("vfbuilders-database-");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static Path folder() throws IOException {
    return Files.createDirectories(ROOT);
  }
}
