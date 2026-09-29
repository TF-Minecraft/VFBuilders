package net.tfminecraft.vfbuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One data directory per JVM, matching Database's static path lifetime. */
final class DatabaseFixture {
  private static final Path ROOT;

  static {
    try {
      ROOT = Files.createTempDirectory("vfbuilders-database-");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static Path folder() throws IOException {
    return Files.createDirectories(ROOT);
  }
}
