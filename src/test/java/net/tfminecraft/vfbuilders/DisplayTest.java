package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Consumer;
import net.tfminecraft.vfbuilders.display.StationTimerDisplay;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;

class DisplayTest {
  ServerMock server;
  World world;
  Location loc;
  TextDisplay display;
  NamespacedKey key;
  Chunk chunk;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getName()).thenReturn("VFBuilders");
    when(VFBuilders.plugin.namespace()).thenReturn("vfbuilders");
    world = mock(World.class);
    when(world.getName()).thenReturn("world");
    loc = new Location(world, 2, 64, 3);
    display = mock(TextDisplay.class);
    when(display.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
    key = new NamespacedKey(VFBuilders.plugin, "station_timer");
    chunk = mock(Chunk.class);
    when(world.getChunkAt(0, 0)).thenReturn(chunk);
    when(world.isChunkLoaded(0, 0)).thenReturn(true);
    when(chunk.getEntities()).thenReturn(new Entity[0]);
  }

  @AfterEach
  void cleanup() {
    VFBuilders.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void labelsAndAnchorsPreserveInput() {
    assertNull(StationTimerDisplay.blockKey(null));
    assertNull(StationTimerDisplay.blockKey(new Location(null, 0, 0, 0)));
    assertEquals("world:2:64:3", StationTimerDisplay.blockKey(loc));
    assertNull(StationTimerDisplay.displayAnchor(null));
    assertEquals(loc.clone().add(.5, 1.35, .5), StationTimerDisplay.displayAnchor(loc));
    assertEquals(64, loc.getY());
    assertTrue(StationTimerDisplay.formatText(null, 60).contains("Vehicle"));
    assertTrue(StationTimerDisplay.formatText("boat", 1, "war").contains("Paused: §7war"));
  }

  @Test
  void spawnValidatesWorldAndConfiguresTaggedDisplay() {
    assertNull(StationTimerDisplay.spawn(null, loc, "t"));
    assertNull(StationTimerDisplay.spawn(world, null, "t"));
    assertNull(StationTimerDisplay.spawn(world, loc, null));
    assertNull(StationTimerDisplay.spawn(world, new Location(null, 0, 0, 0), "t"));
    when(world.spawn(any(Location.class), eq(TextDisplay.class), any(Consumer.class)))
        .thenAnswer(
            a -> {
              Consumer<TextDisplay> action = a.getArgument(2);
              action.accept(display);
              return display;
            });
    assertSame(display, StationTimerDisplay.spawn(world, loc, "timer"));
    verify(display).setText("timer");
    verify(display).setBillboard(Display.Billboard.CENTER);
    verify(display).setAlignment(TextDisplay.TextAlignment.CENTER);
    verify(display).setShadowed(true);
    verify(display).setSeeThrough(false);
    verify(display).setGravity(false);
    verify(display).setInvulnerable(true);
    verify(display).setPersistent(false);
    assertEquals(
        "world:2:64:3", display.getPersistentDataContainer().get(key, PersistentDataType.STRING));
  }

  @Test
  void updateAndRemoveHandleMissingAndDeadEntities() {
    StationTimerDisplay.update(null, loc, "t");
    StationTimerDisplay.update(display, null, "t");
    StationTimerDisplay.update(display, loc, null);
    StationTimerDisplay.update(display, new Location(null, 0, 0, 0), "t");
    when(display.isDead()).thenReturn(true);
    StationTimerDisplay.update(display, loc, "t");
    StationTimerDisplay.remove(display);
    verify(display, never()).remove();
    when(display.isDead()).thenReturn(false);
    StationTimerDisplay.update(display, loc, "t");
    verify(display).setText("t");
    StationTimerDisplay.remove(display);
    verify(display).remove();
    StationTimerDisplay.remove(null);
    StationTimerDisplay.removeById(null);
    StationTimerDisplay.removeById(UUID.randomUUID());
    var entity = server.addPlayer();
    StationTimerDisplay.removeById(entity.getUniqueId());
    assertTrue(entity.isValid());
    try (var b = mockStatic(Bukkit.class)) {
      var id = UUID.randomUUID();
      b.when(() -> Bukkit.getEntity(id)).thenReturn(display);
      StationTimerDisplay.removeById(id);
      verify(display, times(2)).remove();
    }
  }

  @Test
  void purgeRemovesOnlyNearbyMatchingTimersAndInvisibleMarkers() {
    StationTimerDisplay.purgeTaggedInChunk(null, 0, 0);
    when(world.isChunkLoaded(0, 0)).thenReturn(false);
    StationTimerDisplay.purgeTaggedInChunk(world, 0, 0);
    StationTimerDisplay.purgeAtStationBlock(loc);
    StationTimerDisplay.purgeLegacyArmorStands(loc);
    when(world.isChunkLoaded(0, 0)).thenReturn(true);
    StationTimerDisplay.purgeAtStationBlock(null);
    StationTimerDisplay.purgeAtStationBlock(new Location(null, 0, 0, 0));
    StationTimerDisplay.purgeLegacyArmorStands(null);
    StationTimerDisplay.purgeLegacyArmorStands(new Location(null, 0, 0, 0));
    var other = mock(TextDisplay.class);
    when(other.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
    var stand = mock(ArmorStand.class);
    when(stand.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
    when(stand.getLocation()).thenReturn(loc.clone().add(.5, 1.35, .5));
    when(display.getLocation()).thenReturn(loc.clone().add(.5, 1.35, .5));
    when(chunk.getEntities()).thenReturn(new Entity[] {display, other, stand});
    StationTimerDisplay.purgeAtStationBlock(loc);
    verify(display, never()).remove();
    display.getPersistentDataContainer().set(key, PersistentDataType.STRING, "world:2:64:3");
    StationTimerDisplay.purgeAtStationBlock(loc);
    verify(display).remove();
    when(display.getLocation()).thenReturn(loc.clone().add(10, 0, 0));
    StationTimerDisplay.purgeAtStationBlock(loc);
    verify(display, times(1)).remove();
    when(stand.isVisible()).thenReturn(true);
    StationTimerDisplay.purgeLegacyArmorStands(loc);
    when(stand.isVisible()).thenReturn(false);
    StationTimerDisplay.purgeLegacyArmorStands(loc);
    when(stand.isMarker()).thenReturn(true);
    StationTimerDisplay.purgeLegacyArmorStands(loc);
    verify(stand).remove();
    when(stand.getLocation()).thenReturn(loc.clone().add(10, 0, 0));
    StationTimerDisplay.purgeLegacyArmorStands(loc);
    verify(stand, times(1)).remove();
    StationTimerDisplay.purgeTaggedInChunk(world, 0, 0);
    verify(display, times(2)).remove();
    verify(other, never()).remove();
  }
}
