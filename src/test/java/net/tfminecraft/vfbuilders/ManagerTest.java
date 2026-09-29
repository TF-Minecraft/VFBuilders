package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.loaders.*;
import net.tfminecraft.vfbuilders.managers.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class ManagerTest {
  ServerMock server;
  StationManager manager;
  MockedStatic<TLibs> libs;
  BlockAPI api;
  Station definition;
  Block block;
  Player player;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getName()).thenReturn("VFBuilders");
    when(VFBuilders.plugin.namespace()).thenReturn("vfbuilders");
    when(VFBuilders.plugin.isEnabled()).thenReturn(true);
    when(VFBuilders.plugin.getServer()).thenReturn(server);
    api = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getBlockAPI).thenReturn(api);
    StationLoader.get().clear();
    definition = mock(Station.class);
    when(definition.getBlock()).thenReturn("v.stone");
    StationLoader.get().put("dock", definition);
    block = player.getWorld().getBlockAt(1, 64, 1);
    when(api.getChecker().checkBlock(block, "v.stone")).thenReturn(true);
    manager = new StationManager();
  }

  @AfterEach
  void teardown() {
    libs.close();
    MockBukkit.unmock();
    VFBuilders.plugin = null;
  }

  @SuppressWarnings("unchecked")
  Map<UUID, ActivePlacement> placements() throws Exception {
    var f = StationManager.class.getDeclaredField("activePlacements");
    f.setAccessible(true);
    return (Map<UUID, ActivePlacement>) f.get(manager);
  }

  @Test
  void lookupCachesOnlyMatchingStations() {
    assertNull(manager.getStation(player.getWorld().getBlockAt(9, 1, 1)));
    var s = manager.getStation(block);
    assertSame(s, manager.getStation(block));
    assertSame(definition, s.getStation());
  }

  @Test
  void cancelledBlockBreakPreservesStationAndConstruction() {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    s.selectBlueprint(b, null);
    var event = new BlockBreakEvent(block, player);
    event.setCancelled(true);
    manager.onBlockBreak(event);
    assertSame(s, manager.getStation(block));
    verify(b, never()).drop(any());
  }

  @Test
  void worldChangeCannotCrashPlacementOrConsumeMaterials() throws Exception {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    when(b.hasInputs(player)).thenReturn(true);
    placements().put(player.getUniqueId(), new ActivePlacement(player, s, b));
    player.teleport(new Location(server.addSimpleWorld("elsewhere"), 0, 64, 0));
    var event =
        new PlayerInteractEvent(
            player, Action.LEFT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.SELF);
    assertDoesNotThrow(() -> manager.onPlayerLeftClick(event));
    verify(b, never()).takeInputs(player);
  }

  @Test
  void commandsRespectUsagePermissionAndCompletion() {
    var m = new CommandManager();
    var cmd = mock(org.bukkit.command.Command.class);
    assertTrue(m.onCommand(player, cmd, "vfbuilders", new String[0]));
    assertTrue(m.onCommand(player, cmd, "vfbuilders", new String[] {"wrong"}));
    assertTrue(m.onCommand(player, cmd, "vfbuilders", new String[] {"reload"}));
    verify(VFBuilders.plugin, never()).reload();
    assertTrue(m.onTabComplete(player, cmd, "", new String[] {"r"}).isEmpty());
    player.setOp(true);
    assertEquals(List.of("reload"), m.onTabComplete(player, cmd, "", new String[] {"RE"}));
    assertTrue(m.onTabComplete(player, cmd, "", new String[] {"x"}).isEmpty());
    assertTrue(m.onTabComplete(player, cmd, "", new String[0]).isEmpty());
    assertTrue(m.onCommand(player, cmd, "", new String[] {"RELOAD"}));
    verify(VFBuilders.plugin).reload();
    player.setOp(false);
    var permitted = mock(Player.class);
    when(permitted.hasPermission("vfbuilders.reload")).thenReturn(true);
    assertTrue(m.onCommand(permitted, cmd, "", new String[] {"reload"}));
    assertTrue(m.onCommand(server.getConsoleSender(), cmd, "", new String[] {"reload"}));
    verify(VFBuilders.plugin, times(3)).reload();
  }

  org.bukkit.event.inventory.InventoryClickEvent click(
      ActiveStation station,
      org.bukkit.inventory.ItemStack item,
      net.tfminecraft.vfbuilders.enums.VFBGUI type) {
    var e = mock(org.bukkit.event.inventory.InventoryClickEvent.class, RETURNS_DEEP_STUBS);
    when(e.getView().getTopInventory().getHolder())
        .thenReturn(new net.tfminecraft.vfbuilders.holders.VFBHolder(station, type));
    when(e.getWhoClicked()).thenReturn(player);
    when(e.getCurrentItem()).thenReturn(item);
    return e;
  }

  org.bukkit.inventory.ItemStack tagged(String key, String id) {
    var i = new org.bukkit.inventory.ItemStack(Material.PAPER);
    var m = i.getItemMeta();
    m.getPersistentDataContainer()
        .set(
            new NamespacedKey(VFBuilders.plugin, key),
            org.bukkit.persistence.PersistentDataType.STRING,
            id);
    i.setItemMeta(m);
    return i;
  }

  PlayerInteractEvent left() {
    return new PlayerInteractEvent(
        player, Action.LEFT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.SELF);
  }

  @Test
  void menuClicksDispatchCategoriesAndStartPlacementOnlyWithMaterials() throws Exception {
    var field = StationManager.class.getDeclaredField("inv");
    field.setAccessible(true);
    var menus = mock(InventoryManager.class);
    field.set(manager, menus);
    var s = manager.getStation(block);
    manager.inventoryClick(
        mock(org.bukkit.event.inventory.InventoryClickEvent.class, RETURNS_DEEP_STUBS));
    manager.inventoryClick(click(s, null, net.tfminecraft.vfbuilders.enums.VFBGUI.CATEGORY));
    manager.inventoryClick(
        click(
            s,
            new org.bukkit.inventory.ItemStack(Material.BARRIER),
            net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    verify(menus).categoryView(null, player, s, true);
    manager.inventoryClick(
        click(
            s,
            new org.bukkit.inventory.ItemStack(Material.BARRIER),
            net.tfminecraft.vfbuilders.enums.VFBGUI.CATEGORY));
    manager.inventoryClick(
        click(
            s,
            new org.bukkit.inventory.ItemStack(Material.PAPER),
            net.tfminecraft.vfbuilders.enums.VFBGUI.CATEGORY));
    var category = mock(BlueprintCategory.class);
    CategoryLoader.get().put("boats", category);
    manager.inventoryClick(
        click(
            s,
            tagged("vfb_category_id", "boats"),
            net.tfminecraft.vfbuilders.enums.VFBGUI.CATEGORY));
    verify(menus).blueprintView(null, player, category, s, true);
    var b = mock(Blueprint.class);
    BlueprintLoader.get().put("boat", b);
    var item = tagged("vfb_blueprint_id", "boat");
    manager.inventoryClick(click(s, item, net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    assertTrue(placements().isEmpty());
    when(b.hasInputs(player)).thenReturn(true);
    manager.inventoryClick(click(s, item, net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    assertSame(b, placements().get(player.getUniqueId()).getBlueprint());
    server.getScheduler().performTicks(601);
    assertTrue(placements().isEmpty());
  }

  @Test
  void placementDistanceMaterialsAndSuccessfulCommit() throws Exception {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.Vehicle.class);
    when(vehicle.getName()).thenReturn("Boat");
    when(b.getVehicle()).thenReturn(vehicle);
    manager.onPlayerLeftClick(left());
    manager.onPlayerLeftClick(
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.SELF));
    placements().put(player.getUniqueId(), new ActivePlacement(player, s, b));
    player.teleport(block.getLocation().clone().add(20, 0, 0));
    manager.onPlayerLeftClick(left());
    assertFalse(placements().isEmpty());
    player.teleport(block.getLocation());
    manager.onPlayerLeftClick(left());
    assertTrue(placements().isEmpty());
    verify(b, never()).takeInputs(player);
    when(b.hasInputs(player)).thenReturn(true);
    placements().put(player.getUniqueId(), new ActivePlacement(player, s, b));
    manager.onPlayerLeftClick(left());
    verify(b).takeInputs(player);
    assertTrue(placements().isEmpty());
    assertSame(b, s.getBlueprint());
    assertEquals(player.getUniqueId(), s.getConstructorUuid());
    assertEquals(player.getLocation(), s.getSpawnLocation());
  }

  @Test
  void cancelledBeginCanKeepPlacementForConfirmation() throws Exception {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    when(b.hasInputs(player)).thenReturn(true);
    player.teleport(block.getLocation());
    var plugin = MockBukkit.createMockPlugin();
    var keep = new java.util.concurrent.atomic.AtomicBoolean(true);
    server
        .getPluginManager()
        .registerEvent(
            net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent.class,
            new org.bukkit.event.Listener() {},
            org.bukkit.event.EventPriority.NORMAL,
            (listener, event) -> {
              var e = (net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent) event;
              e.setCancelled(true);
              e.setKeepPlacement(keep.get());
            },
            plugin);
    placements().put(player.getUniqueId(), new ActivePlacement(player, s, b));
    manager.onPlayerLeftClick(left());
    assertFalse(placements().isEmpty());
    keep.set(false);
    manager.onPlayerLeftClick(left());
    assertTrue(placements().isEmpty());
    verify(b, never()).takeInputs(player);
  }

  @Test
  void quitAndBreakRemoveOnlyTheirOwnState() throws Exception {
    var s = manager.getStation(block);
    placements().put(player.getUniqueId(), new ActivePlacement(player, s, mock(Blueprint.class)));
    manager.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    assertTrue(placements().isEmpty());
    manager.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    manager.onBlockBreak(new BlockBreakEvent(player.getWorld().getBlockAt(50, 64, 50), player));
    manager.onBlockBreak(new BlockBreakEvent(block, player));
    assertNotSame(s, manager.getStation(block));
    var furniture = mock(dev.lone.itemsadder.api.Events.FurnitureBreakEvent.class);
    when(furniture.isCancelled()).thenReturn(true);
    manager.onFurnitureBreak(furniture);
    when(furniture.isCancelled()).thenReturn(false);
    var entity = mock(org.bukkit.entity.Entity.class);
    when(entity.getLocation()).thenReturn(block.getLocation());
    when(furniture.getBukkitEntity()).thenReturn(entity);
    when(furniture.getPlayer()).thenReturn(player);
    manager.onFurnitureBreak(furniture);
    manager.onFurnitureBreak(furniture);
  }

  @Test
  void rightClickOpensOnlyAnIdleStation() throws Exception {
    var field = StationManager.class.getDeclaredField("inv");
    field.setAccessible(true);
    var menus = mock(InventoryManager.class);
    field.set(manager, menus);
    manager.stationInteract(left());
    var missing =
        new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            null,
            player.getWorld().getBlockAt(50, 64, 50),
            org.bukkit.block.BlockFace.UP);
    manager.stationInteract(missing);
    var e =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP);
    manager.stationInteract(e);
    var s = manager.getStation(block);
    verify(menus).categoryView(null, player, s, true);
    assertTrue(e.isCancelled());
    s.selectBlueprint(mock(Blueprint.class), null);
    manager.stationInteract(e);
    verifyNoMoreInteractions(menus);
  }

  @Test
  void oldPlacementTimeoutMustNotCancelNewerSelection() throws Exception {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    when(b.hasInputs(player)).thenReturn(true);
    BlueprintLoader.get().put("boat", b);
    var item = tagged("vfb_blueprint_id", "boat");
    player.teleport(block.getLocation());
    manager.inventoryClick(click(s, item, net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    server.getScheduler().performTicks(10);
    manager.inventoryClick(click(s, item, net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    var current = placements().get(player.getUniqueId());
    server.getScheduler().performTicks(591);
    assertSame(current, placements().get(player.getUniqueId()));
    server.getScheduler().performTicks(10);
    assertTrue(placements().isEmpty());
  }

  @Test
  void particleTrailStopsAfterCommitOrAnotherConstructionAndIgnoresOtherWorlds() throws Exception {
    var s = manager.getStation(block);
    var b = mock(Blueprint.class);
    when(b.hasInputs(player)).thenReturn(true);
    BlueprintLoader.get().put("boat", b);
    var item = tagged("vfb_blueprint_id", "boat");
    player.teleport(block.getLocation());
    manager.inventoryClick(click(s, item, net.tfminecraft.vfbuilders.enums.VFBGUI.BLUEPRINT));
    server.getScheduler().performTicks(1);
    player.teleport(new Location(server.addSimpleWorld("away"), 0, 64, 0));
    assertDoesNotThrow(() -> server.getScheduler().performTicks(5));
    s.selectBlueprint(b, null);
    server.getScheduler().performTicks(5);
    assertTrue(placements().isEmpty());
    server.getScheduler().performTicks(600);
    manager.onBlockBreak(new BlockBreakEvent(block, player));
    verify(b).drop(any());
  }
}
