package net.tfminecraft.vfbuilders.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import net.tfminecraft.vfbuilders.DatabaseFixture;
import net.tfminecraft.vfbuilders.VFBuilders;
import net.tfminecraft.vfbuilders.core.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;

class StationWriterTest {
  private Path file;

  @BeforeEach
  void plugin() throws Exception {
    Path folder = DatabaseFixture.folder();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getDataFolder()).thenReturn(folder.toFile());
    // Database fixes its path when the class first loads, possibly under another test's folder.
    file = Database.path();
    Files.createDirectories(file.getParent());
  }

  @AfterEach
  void cleanUp() throws Exception {
    Database.close();
    VFBuilders.plugin = null;
    Files.deleteIfExists(file);
  }

  private static Map<Location, ActiveStation> stations(int timeLeft) {
    var world = mock(World.class);
    when(world.getName()).thenReturn("build-world");
    var loc = new Location(world, 3, 64, 5);
    var definition = mock(Station.class);
    when(definition.getId()).thenReturn("dock");
    var station = mock(ActiveStation.class);
    when(station.getLocation()).thenReturn(loc);
    when(station.getStation()).thenReturn(definition);
    when(station.getTimeLeft()).thenReturn(timeLeft);
    return Map.of(loc, station);
  }

  @Test
  void backgroundSaveWritesTheSnapshotTakenOnTheCallingThread() throws Exception {
    var stations = stations(41);
    var station = stations.values().iterator().next();
    var saved = Database.saveStationsLater(stations);
    // Later changes are not part of the queued snapshot.
    when(station.getTimeLeft()).thenReturn(7);
    assertTrue(saved.get(5, TimeUnit.SECONDS));
    assertTrue(Files.readString(file).contains("\"timeLeft\": 41"));
    try (var paths = Files.list(file.getParent())) {
      assertEquals(List.of(file), paths.toList());
    }
  }

  @Test
  void queuedOlderSnapshotNeverOverwritesALaterSynchronousSave() throws Exception {
    CompletableFuture<Boolean> older;
    synchronized (Database.WRITE_LOCK) {
      older = Database.saveStationsLater(stations(30));
      // The writer thread is now waiting for the lock; this synchronous save is newer.
      assertTrue(Database.saveStationsNow(stations(12)));
    }
    assertTrue(older.get(5, TimeUnit.SECONDS));
    assertTrue(Files.readString(file).contains("\"timeLeft\": 12"));
    assertTrue(Database.saveStationsLater(stations(11)).get(5, TimeUnit.SECONDS));
    assertTrue(Files.readString(file).contains("\"timeLeft\": 11"));
  }

  @Test
  void snapshotFailureIsReportedWithoutQueueingAWrite() throws Exception {
    Files.writeString(file, "[]");
    var detached = mock(ActiveStation.class);
    when(detached.getLocation()).thenReturn(new Location(null, 1, 2, 3));
    assertFalse(Database.saveStationsLater(Map.of(new Location(null, 1, 2, 3), detached)).get());
    assertEquals("[]", Files.readString(file));
  }

  @Test
  void closeDrainsQueuedWritesAndTheWriterRestartsAfterwards() throws Exception {
    Database.close(); // Nothing started yet.
    CompletableFuture<Boolean> queued;
    synchronized (Database.WRITE_LOCK) {
      queued = Database.saveStationsLater(stations(9));
    }
    Database.close();
    assertTrue(queued.isDone());
    assertTrue(queued.get());
    assertTrue(Files.readString(file).contains("\"timeLeft\": 9"));
    assertTrue(Database.saveStationsLater(stations(8)).get(5, TimeUnit.SECONDS));
    assertTrue(Files.readString(file).contains("\"timeLeft\": 8"));
  }

  @Test
  void closeGivesUpWaitingForAStuckWriter() throws Exception {
    var logger = mock(Logger.class);
    when(VFBuilders.plugin.getLogger()).thenReturn(logger);
    CompletableFuture<Boolean> stuck;
    synchronized (Database.WRITE_LOCK) {
      stuck = Database.saveStationsLater(stations(5));
      Database.close(Duration.ofMillis(50));
      verify(logger).warning(contains("Station writer still busy"));
    }
    assertTrue(stuck.get(5, TimeUnit.SECONDS));
  }

  @Test
  void interruptedCloseKeepsTheInterruptFlag() throws Exception {
    CompletableFuture<Boolean> pending;
    synchronized (Database.WRITE_LOCK) {
      pending = Database.saveStationsLater(stations(4));
      Thread.currentThread().interrupt();
      Database.close();
      assertTrue(Thread.interrupted());
    }
    assertTrue(pending.get(5, TimeUnit.SECONDS));
  }
}
