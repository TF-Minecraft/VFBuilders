package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.data.Database;
import net.tfminecraft.vfbuilders.display.StationTimerDisplay;
import net.tfminecraft.vfbuilders.managers.StationManager;
import org.bukkit.*;
import org.bukkit.event.world.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class SchedulerTest {
  @TempDir Path temp;

  @Test
  void ticksPauseIncompleteWorkFinishReadyWorkAndRefreshOnlyLoadedChunks() throws Exception {
    var server = MockBukkit.mock();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getDataFolder()).thenReturn(DatabaseFixture.folder().toFile());
    when(VFBuilders.plugin.isEnabled()).thenReturn(true);
    when(VFBuilders.plugin.getName()).thenReturn("VFBuilders");
    try (var db = mockStatic(Database.class);
        var display = mockStatic(StationTimerDisplay.class)) {
      var world = mock(World.class);
      var otherWorld = mock(World.class);
      when(world.isChunkLoaded(0, 0)).thenReturn(true);
      var chunk = mock(Chunk.class);
      when(chunk.getWorld()).thenReturn(world);
      var ready = mock(ActiveStation.class);
      when(ready.hasBlueprint()).thenReturn(true);
      when(ready.tick()).thenReturn(true);
      when(ready.getLocation()).thenReturn(new Location(world, 1, 64, 1));
      when(ready.hasSpawnLocation()).thenReturn(true);
      when(ready.getSpawnLocation()).thenReturn(new Location(world, 3, 64, 3));
      var idle = mock(ActiveStation.class);
      when(idle.getLocation()).thenReturn(new Location(world, 2, 64, 2));
      var waiting = mock(ActiveStation.class);
      when(waiting.hasBlueprint()).thenReturn(true);
      when(waiting.getLocation()).thenReturn(new Location(world, 50, 64, 1));
      when(waiting.hasSpawnLocation()).thenReturn(true);
      when(waiting.getSpawnLocation()).thenReturn(new Location(world, 50, 64, 2));
      var z = mock(ActiveStation.class);
      when(z.getLocation()).thenReturn(new Location(world, 1, 64, 50));
      var elsewhere = mock(ActiveStation.class);
      when(elsewhere.getLocation()).thenReturn(new Location(otherWorld, 1, 64, 1));
      var detached = mock(ActiveStation.class);
      when(detached.hasBlueprint()).thenReturn(true);
      when(detached.getLocation()).thenReturn(new Location(null, 0, 0, 0));
      when(detached.hasSpawnLocation()).thenReturn(true);
      when(detached.getSpawnLocation()).thenReturn(new Location(null, 0, 0, 0));
      var map = new HashMap<Location, ActiveStation>();
      for (var s : List.of(ready, idle, waiting, z, elsewhere, detached))
        map.put(s.getLocation(), s);
      db.when(Database::loadStations).thenReturn(map);
      var manager = new StationManager();
      manager.start();
      server.getScheduler().performTicks(21);
      verify(ready, atLeastOnce()).complete();
      verify(waiting, never()).complete();
      verify(idle, never()).tick();
      verify(ready, atLeastOnce()).ensureDisplay();
      verify(detached, atLeastOnce()).removeDisplay();
      manager.onChunkLoad(new ChunkLoadEvent(chunk, false));
      manager.onChunkUnload(new ChunkUnloadEvent(chunk));
      verify(ready).removeDisplay();
      var reload = mock(Runnable.class);
      manager.rebindAfterReload(reload);
      verify(reload).run();
      verify(waiting).rebindDefinitions();
      manager.stop();
      db.verify(() -> Database.saveStations(map));
    } finally {
      MockBukkit.unmock();
      VFBuilders.plugin = null;
    }
  }
}
