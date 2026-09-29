package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.data.Database;
import net.tfminecraft.vfbuilders.loaders.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class DatabaseTest {
  @Test
  void savesRestoresLegacyAndOwnedProjectsAndHandlesIoFailures() throws Exception {
    var server = MockBukkit.mock();
    Path temp = DatabaseFixture.folder();
    try {
      VFBuilders.plugin = mock(VFBuilders.class);
      when(VFBuilders.plugin.getDataFolder()).thenReturn(temp.toFile());
      Files.createDirectories(temp.resolve("data"));
      var file = temp.resolve("data/stations.json");
      var world = server.addSimpleWorld("build-world");
      var loc = new Location(world, 3, 64, 5);
      var definition = mock(Station.class);
      when(definition.getId()).thenReturn("dock");
      StationLoader.get().clear();
      BlueprintLoader.get().clear();
      StationLoader.get().put("dock", definition);
      new Database();
      assertTrue(Database.loadStations().isEmpty());
      var empty = new ActiveStation(loc, definition);
      Database.saveStations(Map.of(loc, empty));
      var loaded = Database.loadStations();
      assertEquals(1, loaded.size());
      assertNull(loaded.get(loc).getConstructorUuid());
      assertFalse(loaded.get(loc).hasBlueprint());
      var blueprint = mock(Blueprint.class);
      when(blueprint.getId()).thenReturn("boat");
      when(blueprint.getTime()).thenReturn(40);
      BlueprintLoader.get().put("boat", blueprint);
      try (var display = mockStatic(net.tfminecraft.vfbuilders.display.StationTimerDisplay.class)) {
        world.unloadChunk(0, 0);
        empty.selectBlueprint(blueprint, null);
        empty.setSpawnLocation(loc.clone().add(2, 0, 0));
        Database.saveStations(Map.of(loc, empty));
        assertTrue(Database.loadStations().get(loc).hasBlueprint());
        var uuid = UUID.randomUUID();
        empty.setConstructorUuid(uuid);
        empty.setTimeLeft(12);
        Database.saveStations(Map.of(loc, empty));
        var restored = Database.loadStations().get(loc);
        assertEquals(uuid, restored.getConstructorUuid());
        assertEquals(12, restored.getTimeLeft());
        assertEquals(empty.getSpawnLocation(), restored.getSpawnLocation());
        String valid = Files.readString(file);
        Files.writeString(file, valid.replace(uuid.toString(), "invalid"));
        assertNull(Database.loadStations().get(loc).getConstructorUuid());
        Files.writeString(file, valid.replace(uuid.toString(), ""));
        assertNull(Database.loadStations().get(loc).getConstructorUuid());
        StationLoader.get().clear();
        assertTrue(Database.loadStations().isEmpty());
      }
      Files.delete(file);
      Files.createDirectory(file);
      assertTrue(Database.loadStations().isEmpty());
      assertDoesNotThrow(() -> Database.saveStations(Map.of()));
    } finally {
      VFBuilders.plugin = null;
      MockBukkit.unmock();
      try (var paths = Files.walk(temp)) {
        for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
      }
    }
  }
}
