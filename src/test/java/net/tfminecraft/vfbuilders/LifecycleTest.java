package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.vehicleframework.VFLogger;
import net.tfminecraft.vfbuilders.data.Database;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class LifecycleTest {
  @Test
  void enableReloadDisableAndExistingConfiguration() throws Exception {
    var server = MockBukkit.mock();
    MockBukkit.createMockPlugin("TLibs");
    MockBukkit.createMockPlugin("VehicleFramework");
    // The persistence test exercises real files; lifecycle isolates that boundary because its
    // legacy path is static.
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getDataFolder()).thenReturn(DatabaseFixture.folder().toFile());
    try (var db = mockStatic(Database.class);
        var log = mockStatic(VFLogger.class)) {
      db.when(Database::loadStations).thenReturn(new HashMap<>());
      var p = MockBukkit.load(VFBuilders.class);
      assertSame(p, VFBuilders.plugin);
      assertNotNull(p.getCommand("vfbuilders").getExecutor());
      assertNotNull(p.getCommand("vfbuilders").getTabCompleter());
      assertTrue(p.getDataFolder().toPath().resolve("blueprints").toFile().isDirectory());
      p.createFolders();
      p.createConfigs();
      p.reload();
      server.getScheduler().performTicks(21);
      p.onDisable();
      db.verify(() -> Database.saveStations(anyMap()));
      var folder = p.getDataFolder().toPath().resolve("blueprints");
      Files.writeString(folder.resolve("empty.yml"), "");
      Files.createDirectory(folder.resolve("ignored"));
      p.loadConfigs();
      Files.setPosixFilePermissions(folder, java.util.Set.of());
      try {
        p.loadConfigs();
      } finally {
        Files.setPosixFilePermissions(
            folder, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
      }
      Files.delete(folder.resolve("empty.yml"));
      Files.delete(folder.resolve("ignored"));
      Files.delete(folder);
      p.loadConfigs();
      Files.writeString(folder, "not a directory");
      p.loadConfigs();
      try (var paths = Files.walk(p.getDataFolder().toPath())) {
        for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
          Files.delete(path);
      }
      p.createFolders();
      assertTrue(p.getDataFolder().isDirectory());
    } finally {
      MockBukkit.unmock();
      VFBuilders.plugin = null;
    }
  }
}
