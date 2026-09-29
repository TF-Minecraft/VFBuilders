package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.vehicleframework.*;
import net.tfminecraft.vehicleframework.managers.VehicleManager;
import net.tfminecraft.vehicleframework.vehicles.*;
import net.tfminecraft.vehicleframework.vehicles.Vehicle;
import net.tfminecraft.vfbuilders.api.*;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.display.*;
import net.tfminecraft.vfbuilders.loaders.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class StationTest {
  ServerMock server;
  World world;
  Location loc;
  Blueprint b;
  Station definition;
  MockedStatic<StationTimerDisplay> displays;
  MockedStatic<ConstructionFreezes> freezes;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = mock(World.class);
    loc = new Location(world, 1, 64, 3);
    b = mock(Blueprint.class);
    definition = mock(Station.class);
    when(b.getTime()).thenReturn(2);
    when(b.getId()).thenReturn("boat");
    when(definition.getId()).thenReturn("dock");
    displays = mockStatic(StationTimerDisplay.class);
    freezes = mockStatic(ConstructionFreezes.class);
    BlueprintLoader.get().clear();
    StationLoader.get().clear();
  }

  @AfterEach
  void cleanup() {
    displays.close();
    freezes.close();
    MockBukkit.unmock();
  }

  @Test
  void selectionCountdownFreezeAndCancellation() {
    var s = new ActiveStation(loc, definition);
    assertNotNull(s.getUuid());
    assertSame(loc, s.getLocation());
    assertSame(definition, s.getStation());
    assertFalse(s.hasBlueprint());
    assertFalse(s.hasSpawnLocation());
    assertNull(s.getSpawnLocation());
    assertNull(s.getBlueprint());
    assertEquals(0, s.getTimeLeft());
    assertNull(s.getConstructorUuid());
    assertTrue(s.tick());
    s.ensureDisplay();
    s.complete();
    var id = UUID.randomUUID();
    s.selectBlueprint(b, id);
    assertTrue(s.hasBlueprint());
    assertEquals(2, s.getTimeLeft());
    assertEquals(id, s.getConstructorUuid());
    freezes.when(() -> ConstructionFreezes.reason(id)).thenReturn("pause");
    assertFalse(s.tick());
    assertEquals(2, s.getTimeLeft());
    freezes.when(() -> ConstructionFreezes.reason(id)).thenReturn(null);
    assertFalse(s.tick());
    assertTrue(s.tick());
    assertEquals(0, s.getTimeLeft());
    s.setSpawnLocation(loc);
    assertTrue(s.hasSpawnLocation());
    s.cancelConstruction();
    verify(b).drop(eq(loc.clone().add(.5, 1, .5)));
    assertFalse(s.hasBlueprint());
    assertFalse(s.hasSpawnLocation());
    assertNull(s.getConstructorUuid());
    s.cancelConstruction();
    s.setTimeLeft(-1);
    assertTrue(s.tick());
    s.setConstructorUuid(id);
    assertEquals(id, s.getConstructorUuid());
  }

  @Test
  void restorationAndReloadRebindOrRefund() {
    var old = new ActiveStation(loc, definition, null, 3, null);
    assertFalse(old.hasBlueprint());
    var missing = new ActiveStation(loc, null, "missing", 4, loc);
    assertFalse(missing.hasBlueprint());
    assertSame(loc, missing.getSpawnLocation());
    missing.rebindDefinitions();
    missing.complete();
    old.rebindDefinitions();
    BlueprintLoader.get().put("boat", b);
    var s = new ActiveStation(loc, definition, "boat", 5, loc, UUID.randomUUID());
    assertSame(b, s.getBlueprint());
    s.rebindDefinitions();
    assertSame(definition, s.getStation());
    var replacement = mock(Station.class);
    StationLoader.get().put("dock", replacement);
    var updated = mock(Blueprint.class);
    when(updated.getId()).thenReturn("boat");
    BlueprintLoader.get().put("boat", updated);
    s.rebindDefinitions();
    assertSame(replacement, s.getStation());
    assertSame(updated, s.getBlueprint());
    BlueprintLoader.get().clear();
    s.rebindDefinitions();
    assertFalse(s.hasBlueprint());
    verify(updated).drop(any());
  }

  @Test
  void displaysWaitForLoadedChunksAndReuseLivingEntity() {
    var noWorld = new ActiveStation(new Location(null, 0, 0, 0), definition);
    noWorld.selectBlueprint(b, null);
    when(world.isChunkLoaded(0, 0)).thenReturn(true);
    var vehicle = mock(Vehicle.class);
    when(vehicle.getName()).thenReturn("boat");
    when(b.getVehicle()).thenReturn(vehicle);
    displays.when(() -> StationTimerDisplay.formatText("boat", 2, null)).thenReturn("timer");
    TextDisplay display = mock(TextDisplay.class);
    displays.when(() -> StationTimerDisplay.spawn(world, loc, "timer")).thenReturn(display);
    var s = new ActiveStation(loc, definition);
    s.selectBlueprint(b, null);
    s.ensureDisplay();
    displays.verify(() -> StationTimerDisplay.update(display, loc, "timer"));
    when(display.isDead()).thenReturn(true);
    s.ensureDisplay();
    displays.verify(() -> StationTimerDisplay.spawn(world, loc, "timer"), times(2));
    s.removeHolograms();
  }

  @Test
  void completionWaitsForNearbyPlayersAndRetriesFailedSpawn() {
    var s = new ActiveStation(loc, definition);
    s.selectBlueprint(b, null);
    s.complete();
    s.setSpawnLocation(loc);
    when(world.getPlayers()).thenReturn(List.of());
    s.complete();
    assertTrue(s.hasBlueprint());
    var p = mock(Player.class);
    when(p.getLocation()).thenReturn(loc.clone().add(97, 0, 0));
    when(world.getPlayers()).thenReturn(List.of(p));
    s.complete();
    assertTrue(s.hasBlueprint());
    when(p.getLocation()).thenReturn(loc.clone().add(96, 0, 0));
    try (var vf = mockStatic(VehicleFramework.class);
        var logger = mockStatic(VFLogger.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      s.complete();
      assertTrue(s.hasBlueprint());
      var v = mock(ActiveVehicle.class);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(v);
      s.complete();
      assertFalse(s.hasBlueprint());
      assertFalse(s.hasSpawnLocation());
      assertNull(s.getConstructorUuid());
      verify(world)
          .playSound(any(Location.class), eq(Sound.BLOCK_BEACON_ACTIVATE), eq(.8f), eq(1.6f));
    }
  }
}
