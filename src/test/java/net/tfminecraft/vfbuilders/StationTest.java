package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.vehicleframework.*;
import net.tfminecraft.vehicleframework.database.VehiclePersistence;
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
  MockedStatic<VehiclePersistence> persistenceAccess;
  VehiclePersistence persistence;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getName()).thenReturn("VFBuilders");
    world = mock(World.class);
    loc = new Location(world, 1, 64, 3);
    b = mock(Blueprint.class);
    definition = mock(Station.class);
    when(b.getTime()).thenReturn(2);
    when(b.getId()).thenReturn("boat");
    when(definition.getId()).thenReturn("dock");
    displays = mockStatic(StationTimerDisplay.class);
    freezes = mockStatic(ConstructionFreezes.class);
    persistence = mock(VehiclePersistence.class, RETURNS_DEEP_STUBS);
    when(persistence.saveLive(any(ActiveVehicle.class))).thenReturn(true);
    persistenceAccess = mockStatic(VehiclePersistence.class);
    persistenceAccess.when(VehiclePersistence::current).thenReturn(persistence);
    BlueprintLoader.get().clear();
    StationLoader.get().clear();
  }

  @AfterEach
  void cleanup() {
    displays.close();
    freezes.close();
    persistenceAccess.close();
    MockBukkit.unmock();
    VFBuilders.plugin = null;
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
    freezes.when(() -> ConstructionFreezes.reason(id, null)).thenReturn("pause");
    assertFalse(s.tick());
    assertEquals(2, s.getTimeLeft());
    freezes.when(() -> ConstructionFreezes.reason(id, null)).thenReturn(null);
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
  void characterIsAskedForFreezesAndCarriedToCompletionAndCancellation() {
    var account = UUID.randomUUID();
    var character = UUID.randomUUID();
    var s = new ActiveStation(loc, definition);
    s.selectBlueprint(b, account, character);
    assertEquals(account, s.getConstructorUuid());
    assertEquals(character, s.getConstructorCharacterId());
    freezes.when(() -> ConstructionFreezes.reason(account, character)).thenReturn("siege");
    assertFalse(s.tick());
    assertEquals(2, s.getTimeLeft());

    var fired = new ArrayList<org.bukkit.event.Event>();
    var plugin = MockBukkit.createMockPlugin();
    for (var type :
        List.of(
            net.tfminecraft.vfbuilders.events.VehicleConstructEvent.class,
            net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent.class)) {
      server
          .getPluginManager()
          .registerEvent(
              type,
              new org.bukkit.event.Listener() {},
              org.bukkit.event.EventPriority.MONITOR,
              (listener, event) -> fired.add(event),
              plugin);
    }
    s.setSpawnLocation(loc);
    var p = mock(Player.class);
    when(p.getLocation()).thenReturn(loc);
    when(world.getPlayers()).thenReturn(List.of(p));
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(mock(ActiveVehicle.class));
      s.complete();
    }
    var built = (net.tfminecraft.vfbuilders.events.VehicleConstructEvent) fired.get(0);
    assertEquals(account, built.getConstructorUuid());
    assertEquals(character, built.getConstructorCharacterId());
    assertNull(s.getConstructorCharacterId());

    s.selectBlueprint(b, account, character);
    s.cancelConstruction();
    var cancelled = (net.tfminecraft.vfbuilders.events.VehicleConstructionCancelEvent) fired.get(1);
    assertEquals(account, cancelled.getConstructorUuid());
    assertEquals(character, cancelled.getConstructorCharacterId());
    assertNull(s.getConstructorCharacterId());
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
    assertNull(s.getConstructorCharacterId());
    var character = UUID.randomUUID();
    assertEquals(
        character,
        new ActiveStation(loc, definition, "boat", 5, loc, UUID.randomUUID(), character)
            .getConstructorCharacterId());
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

  ActiveStation readyStation() {
    var station = new ActiveStation(loc, definition);
    station.selectBlueprint(b, UUID.randomUUID(), UUID.randomUUID());
    station.setSpawnLocation(loc);
    station.setTimeLeft(0);
    var player = mock(Player.class);
    when(player.getLocation()).thenReturn(loc);
    when(world.getPlayers()).thenReturn(List.of(player));
    return station;
  }

  @Test
  void completionRecordsOutputBeforeEventAndSavesVehicleBeforeClearingTheJob() {
    var station = readyStation();
    var vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("created-vehicle");
    var states = new ArrayList<String>();
    station.setCheckpoint(() -> {
      states.add(station.getOutputUuid() + ":" + station.hasBlueprint());
      return true;
    });
    when(persistence.saveLive(vehicle)).thenAnswer(invocation -> {
      states.add("vehicle-saved");
      return true;
    });
    var plugin = MockBukkit.createMockPlugin();
    server.getPluginManager().registerEvent(
        net.tfminecraft.vfbuilders.events.VehicleConstructEvent.class,
        new org.bukkit.event.Listener() {}, org.bukkit.event.EventPriority.MONITOR,
        (listener, event) -> states.add("event"), plugin);
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(vehicle);
      station.complete();
      assertEquals(List.of("created-vehicle:true", "event", "vehicle-saved", "null:false"), states);
      verify(persistence).saveLive(vehicle);
      assertFalse(station.hasBlueprint());
      assertNull(station.getOutputUuid());
    }
  }

  @Test
  void failedOutputCheckpointRemovesTheUnsavedEntityAndLeavesTheBuildQueued() {
    var station = readyStation();
    station.setCheckpoint(() -> false);
    var vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("created-vehicle");
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(vehicle);
      station.complete();
      verify(vehicle).remove(net.tfminecraft.vehicleframework.enums.VehicleRemoveReason.UNLOAD);
      verify(persistence, never()).saveLive(any(ActiveVehicle.class));
      assertTrue(station.hasBlueprint());
      assertNull(station.getOutputUuid());
    }
  }

  @Test
  void failedVehicleSaveRetriesTheSameEntityAndDoesNotRepeatTheConstructionEvent() {
    var station = readyStation();
    var vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("created-vehicle");
    when(persistence.saveLive(vehicle)).thenReturn(false, true);
    var events = new ArrayList<org.bukkit.event.Event>();
    var plugin = MockBukkit.createMockPlugin();
    server.getPluginManager().registerEvent(
        net.tfminecraft.vfbuilders.events.VehicleConstructEvent.class,
        new org.bukkit.event.Listener() {}, org.bukkit.event.EventPriority.MONITOR,
        (listener, event) -> events.add(event), plugin);
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(vehicle);
      when(manager.getByUUID("created-vehicle")).thenReturn(vehicle);
      station.complete();
      assertTrue(station.hasBlueprint());
      assertEquals("created-vehicle", station.getOutputUuid());
      station.cancelConstruction();
      assertTrue(station.hasBlueprint());
      station.complete();
      assertFalse(station.hasBlueprint());
      assertEquals(1, events.size());
      verify(manager).spawn(loc, (Vehicle) null);
    }
  }

  @Test
  void savedOutputIncludingADeletedVehicleCannotSpawnAgainAfterAnInterruptedClear() {
    var station = readyStation();
    station.setOutputUuid("saved-output");
    when(persistence.repository().find("saved-output"))
        .thenReturn(Optional.of(mock(net.tfminecraft.vehicleframework.database.VehicleSnapshot.class)));
    var allowSave = new java.util.concurrent.atomic.AtomicBoolean(false);
    station.setCheckpoint(allowSave::get);
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      station.complete();
      assertTrue(station.hasBlueprint());
      assertEquals("saved-output", station.getOutputUuid());
      assertNotNull(station.getConstructorUuid());
      assertNotNull(station.getConstructorCharacterId());
      assertEquals(loc, station.getSpawnLocation());
      allowSave.set(true);
      station.complete();
      assertFalse(station.hasBlueprint());
      verifyNoInteractions(manager);
      verify(b, never()).drop(any());
    }
  }

  @Test
  void missingOutputAfterRestartCanBeRebuiltButUnavailablePersistenceMustWait() {
    var station = readyStation();
    station.setOutputUuid("lost-runtime-output");
    persistenceAccess.when(VehiclePersistence::current).thenReturn(null);
    station.complete();
    station.cancelConstruction();
    assertTrue(station.hasBlueprint());
    persistenceAccess.when(VehiclePersistence::current).thenReturn(persistence);
    var vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("replacement-output");
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(vehicle);
      station.complete();
      assertFalse(station.hasBlueprint());
      verify(manager).spawn(loc, (Vehicle) null);
    }
  }

  @Test
  void cancellationSaveFailureKeepsTheBuildAndStoredOutputCancellationDoesNotRefund() {
    var station = readyStation();
    station.setCheckpoint(() -> false);
    station.cancelConstruction();
    assertTrue(station.hasBlueprint());
    verify(b, never()).drop(any());
    station.setOutputUuid("saved-output");
    when(persistence.repository().find("saved-output"))
        .thenReturn(Optional.of(mock(net.tfminecraft.vehicleframework.database.VehicleSnapshot.class)));
    station.cancelConstruction();
    assertTrue(station.hasBlueprint());
    station.setCheckpoint(() -> true);
    station.setTimeLeft(40);
    station.cancelConstruction();
    assertFalse(station.hasBlueprint());
    assertEquals(0, station.getTimeLeft());
    verify(b, never()).drop(any());
  }

  @Test
  void failedSaveWithAnExistingStoredOutputRecoversWithoutAnotherEventOrSpawn() {
    var station = readyStation();
    var vehicle = mock(ActiveVehicle.class);
    when(vehicle.getUUID()).thenReturn("stored-output");
    when(persistence.saveLive(vehicle)).thenAnswer(invocation -> {
      when(persistence.repository().find("stored-output"))
          .thenReturn(Optional.of(mock(net.tfminecraft.vehicleframework.database.VehicleSnapshot.class)));
      return false;
    });
    var events = new ArrayList<org.bukkit.event.Event>();
    var plugin = MockBukkit.createMockPlugin();
    server.getPluginManager().registerEvent(
        net.tfminecraft.vfbuilders.events.VehicleConstructEvent.class,
        new org.bukkit.event.Listener() {}, org.bukkit.event.EventPriority.MONITOR,
        (listener, event) -> events.add(event), plugin);
    try (var vf = mockStatic(VehicleFramework.class)) {
      var manager = mock(VehicleManager.class);
      vf.when(VehicleFramework::getVehicleManager).thenReturn(manager);
      when(manager.spawn(loc, (Vehicle) null)).thenReturn(vehicle);
      station.complete();
      assertTrue(station.hasBlueprint());
      station.complete();
      assertFalse(station.hasBlueprint());
      assertEquals(1, events.size());
      verify(manager).spawn(loc, (Vehicle) null);
      verify(persistence).saveLive(vehicle);
    }
  }
}
