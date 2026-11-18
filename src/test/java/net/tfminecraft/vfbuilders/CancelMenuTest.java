package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.vehicleframework.VFLogger;
import net.tfminecraft.vehicleframework.vehicles.Vehicle;
import net.tfminecraft.vfbuilders.api.ConstructionFreezes;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.data.Database;
import net.tfminecraft.vfbuilders.enums.VFBGUI;
import net.tfminecraft.vfbuilders.holders.VFBHolder;
import net.tfminecraft.vfbuilders.loaders.*;
import net.tfminecraft.vfbuilders.managers.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

/** The station menu that shows a build's progress and lets its builder (or staff) cancel it. */
class CancelMenuTest {
  ServerMock server;
  StationManager manager;
  InventoryManager menus;
  MockedStatic<TLibs> libs;
  MockedStatic<Database> database;
  MockedStatic<VFLogger> logger;
  BlockAPI blocks;
  ItemAPI items;
  Station definition;
  Block block;
  PlayerMock player;
  Blueprint b;

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    player = server.addPlayer();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.getName()).thenReturn("VFBuilders");
    when(VFBuilders.plugin.namespace()).thenReturn("vfbuilders");
    when(VFBuilders.plugin.isEnabled()).thenReturn(true);
    when(VFBuilders.plugin.getServer()).thenReturn(server);
    blocks = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getBlockAPI).thenReturn(blocks);
    libs.when(TLibs::getItemAPI).thenReturn(items);
    database = mockStatic(Database.class);
    database.when(() -> Database.saveStationsNow(anyMap())).thenReturn(true);
    logger = mockStatic(VFLogger.class);
    StationLoader.get().clear();
    BlueprintLoader.get().clear();
    CategoryLoader.get().clear();
    definition = mock(Station.class);
    when(definition.getBlock()).thenReturn("v.stone");
    StationLoader.get().put("dock", definition);
    block = player.getWorld().getBlockAt(1, 64, 1);
    when(blocks.getChecker().checkBlock(block, "v.stone")).thenReturn(true);
    manager = new StationManager();
    menus = mock(InventoryManager.class);
    var field = StationManager.class.getDeclaredField("inv");
    field.setAccessible(true);
    field.set(manager, menus);
    b = blueprint();
  }

  @AfterEach
  void teardown() {
    logger.close();
    database.close();
    libs.close();
    MockBukkit.unmock();
    VFBuilders.plugin = null;
  }

  Blueprint blueprint() {
    var bp = mock(Blueprint.class);
    var vehicle = mock(Vehicle.class);
    when(vehicle.getName()).thenReturn("Boat");
    when(bp.getVehicle()).thenReturn(vehicle);
    when(bp.getTime()).thenReturn(60);
    when(bp.getId()).thenReturn("boat");
    when(bp.getItem()).thenReturn(new ItemStack(Material.PAPER));
    return bp;
  }

  ActiveStation building(UUID account) {
    var s = manager.getStation(block);
    s.selectBlueprint(b, account);
    return s;
  }

  InventoryClickEvent click(PlayerMock who, ActiveStation s, ItemStack item, VFBGUI type, Blueprint build) {
    var e = mock(InventoryClickEvent.class, RETURNS_DEEP_STUBS);
    when(e.getView().getTopInventory().getHolder()).thenReturn(new VFBHolder(s, type, build));
    when(e.getWhoClicked()).thenReturn(who);
    when(e.getCurrentItem()).thenReturn(item);
    return e;
  }

  ItemStack action(String id) {
    var i = new ItemStack(Material.RED_CONCRETE);
    var m = i.getItemMeta();
    m.getPersistentDataContainer()
        .set(new NamespacedKey(VFBuilders.plugin, "vfb_action"), PersistentDataType.STRING, id);
    i.setItemMeta(m);
    return i;
  }

  PlayerInteractEvent rightClick(PlayerMock who) {
    return new PlayerInteractEvent(who, Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP);
  }

  void drain(PlayerMock who) {
    while (who.nextMessage() != null) {}
  }

  @Test
  void theBuilderSeesProgressConfirmsAndGetsTheMaterialsBack() {
    var s = building(player.getUniqueId());
    manager.stationInteract(rightClick(player));
    verify(menus).progressView(player, s, true, true);
    manager.inventoryClick(click(player, s, action("cancel"), VFBGUI.PROGRESS, b));
    verify(menus).confirmCancelView(player, s, b, true);
    drain(player);
    manager.inventoryClick(click(player, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    verify(b).give(player);
    verify(b, never()).drop(any());
    assertFalse(s.hasBlueprint());
    assertTrue(player.nextMessage().contains("Construction cancelled. Your materials were returned"));
  }

  @Test
  void staffCanCancelSomeoneElsesBuildAndTheMaterialsDropAtTheStation() {
    var s = building(UUID.randomUUID());
    var staff = server.addPlayer();
    staff.setOp(true);
    manager.stationInteract(rightClick(staff));
    verify(menus).progressView(staff, s, true, false);
    manager.inventoryClick(click(staff, s, action("cancel"), VFBGUI.PROGRESS, b));
    verify(menus).confirmCancelView(staff, s, b, false);
    drain(staff);
    manager.inventoryClick(click(staff, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    verify(b).drop(block.getLocation().clone().add(0.5, 1, 0.5));
    verify(b, never()).give(any());
    assertFalse(s.hasBlueprint());
    assertTrue(staff.nextMessage().contains("dropped at the station"));
  }

  @Test
  void anyoneElseSeesProgressButCannotCancel() {
    var s = building(UUID.randomUUID());
    var stranger = server.addPlayer();
    manager.stationInteract(rightClick(stranger));
    verify(menus).progressView(stranger, s, false, false);
    manager.inventoryClick(click(stranger, s, action("cancel"), VFBGUI.PROGRESS, b));
    manager.inventoryClick(click(stranger, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    verify(menus, never()).confirmCancelView(any(), any(), any(), anyBoolean());
    assertSame(b, s.getBlueprint());
    assertTrue(stranger.nextMessage().contains("can no longer be cancelled"));
  }

  @Test
  void staleMenusCannotCancelAnEndedReplacedOrRemovedBuild() {
    var s = building(player.getUniqueId());
    // A menu that never showed a build.
    manager.inventoryClick(click(player, s, action("cancel"), VFBGUI.CATEGORY, null));
    // The station now builds something else.
    manager.inventoryClick(click(player, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, blueprint()));
    // A station that is not registered any more.
    var removed = new ActiveStation(block.getLocation(), definition);
    removed.selectBlueprint(b, player.getUniqueId());
    manager.inventoryClick(click(player, removed, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    // The timer ran out, so the vehicle is about to appear.
    s.setTimeLeft(0);
    manager.inventoryClick(click(player, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    verify(b, never()).give(any());
    verify(b, never()).drop(any());
    verify(menus, never()).confirmCancelView(any(), any(), any(), anyBoolean());
    assertSame(b, s.getBlueprint());
    assertSame(b, removed.getBlueprint());
  }

  @Test
  void aFailedSaveKeepsTheBuildAndSaysSo() {
    var s = building(player.getUniqueId());
    database.when(() -> Database.saveStationsNow(anyMap())).thenReturn(false);
    manager.inventoryClick(click(player, s, action("confirm_cancel"), VFBGUI.CONFIRM_CANCEL, b));
    assertSame(b, s.getBlueprint());
    verify(b, never()).give(any());
    assertTrue(player.nextMessage().contains("could not be cancelled"));
  }

  @Test
  void backFromTheConfirmScreenReopensTheStationMenu() {
    var s = building(player.getUniqueId());
    var back = new ItemStack(Material.BARRIER);
    manager.inventoryClick(click(player, s, back, VFBGUI.CONFIRM_CANCEL, b));
    verify(menus).progressView(player, s, true, true);
    s.cancelConstruction();
    manager.inventoryClick(click(player, s, back, VFBGUI.CONFIRM_CANCEL, b));
    verify(menus).categoryView(null, player, s, true);
    var removed = new ActiveStation(block.getLocation(), definition);
    manager.inventoryClick(click(player, removed, back, VFBGUI.CONFIRM_CANCEL, b));
    verifyNoMoreInteractions(menus);
  }

  @Test
  void buildsAreCancellableOnlyWhileTimeIsLeftAndNoVehicleExists() {
    var s = new ActiveStation(block.getLocation(), definition);
    assertFalse(s.isCancellable());
    s.selectBlueprint(b, player.getUniqueId());
    assertTrue(s.isCancellable());
    s.setOutputUuid("spawned");
    assertFalse(s.isCancellable());
    s.setOutputUuid(null);
    s.setTimeLeft(0);
    assertFalse(s.isCancellable());
  }

  @Test
  void theConstructorIsTheAccountAndCharacterThatStartedTheBuild() {
    var s = new ActiveStation(block.getLocation(), definition);
    var account = UUID.randomUUID();
    var character = UUID.randomUUID();
    assertFalse(s.isConstructor(account, character));
    s.selectBlueprint(b, account, character);
    assertTrue(s.isConstructor(account, character));
    assertFalse(s.isConstructor(account, UUID.randomUUID()));
    assertFalse(s.isConstructor(account, null));
    assertFalse(s.isConstructor(UUID.randomUUID(), character));
    s.selectBlueprint(b, account);
    assertTrue(s.isConstructor(account, null));
    assertTrue(s.isConstructor(account, character));
  }

  @Test
  void aPausedBuildReportsWhy() {
    try (var freezes = mockStatic(ConstructionFreezes.class)) {
      var s = new ActiveStation(block.getLocation(), definition);
      var account = UUID.randomUUID();
      s.selectBlueprint(b, account);
      assertNull(s.getFreezeReason());
      freezes.when(() -> ConstructionFreezes.reason(account, null)).thenReturn("war");
      s.tick();
      assertEquals("war", s.getFreezeReason());
    }
  }

  @Test
  void materialsGoToTheInventoryInNormalStacksAndTheRestDropsAtTheFeet() throws Exception {
    var y = new YamlConfiguration();
    y.loadFromString("inputs: [v.iron 70, v.pearl 3]");
    var real = new Blueprint("b", y);
    when(items.getCreator().getItemFromPath("v.iron")).thenReturn(new ItemStack(Material.IRON_INGOT));
    when(items.getCreator().getItemFromPath("v.pearl")).thenReturn(new ItemStack(Material.ENDER_PEARL));
    real.give(player);
    var inv = player.getInventory();
    assertTrue(inv.containsAtLeast(new ItemStack(Material.IRON_INGOT), 70));
    assertTrue(inv.containsAtLeast(new ItemStack(Material.ENDER_PEARL), 3));
    for (ItemStack i : inv.getStorageContents()) {
      if (i != null) assertTrue(i.getAmount() <= i.getMaxStackSize());
    }
    inv.clear();
    for (int x = 0; x < inv.getSize(); x++) inv.setItem(x, new ItemStack(Material.DIRT, 64));
    real.give(player);
    assertEquals(3, player.getWorld().getEntities().stream().filter(e -> e instanceof Item).count());
  }

  @Test
  void cancellingForAPlayerGivesInsteadOfDropping() {
    var s = new ActiveStation(block.getLocation(), definition);
    s.selectBlueprint(b, player.getUniqueId());
    s.cancelConstruction(player);
    verify(b).give(player);
    verify(b, never()).drop(any());
  }

  @Nested
  class Views {
    InventoryManager real = new InventoryManager();
    ActiveStation s = mock(ActiveStation.class);

    @BeforeEach
    void station() {
      when(s.getBlueprint()).thenReturn(b);
      when(s.getTimeLeft()).thenReturn(90);
    }

    org.bukkit.inventory.Inventory top() {
      return player.getOpenInventory().getTopInventory();
    }

    String action(ItemStack i) {
      return i.getItemMeta()
          .getPersistentDataContainer()
          .get(new NamespacedKey(VFBuilders.plugin, "vfb_action"), PersistentDataType.STRING);
    }

    @Test
    void theBuilderGetsTheCancelButtonAndThePauseReason() {
      when(s.isCancellable()).thenReturn(true);
      when(s.getFreezeReason()).thenReturn("war");
      real.progressView(player, s, true, true);
      var holder = (VFBHolder) top().getHolder();
      assertEquals(VFBGUI.PROGRESS, holder.getType());
      assertSame(b, holder.getBuild());
      var info = top().getItem(11);
      assertEquals("§eConstructing §6Boat", info.getItemMeta().getDisplayName());
      assertTrue(info.getItemMeta().getLore().toString().contains("Paused: §7war"));
      assertNull(action(info));
      var cancel = top().getItem(15);
      assertEquals("cancel", action(cancel));
      assertTrue(cancel.getItemMeta().getLore().toString().contains("your inventory"));
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(0).getType());
    }

    @Test
    void staffAreToldTheMaterialsDropAtTheStation() {
      when(s.isCancellable()).thenReturn(true);
      real.progressView(player, s, true, false);
      assertTrue(top().getItem(15).getItemMeta().getLore().toString().contains("drop at"));
      assertFalse(top().getItem(11).getItemMeta().getLore().toString().contains("Paused"));
    }

    @Test
    void othersGetNoButtonAndAreToldWhoCanCancel() {
      when(s.isCancellable()).thenReturn(true);
      real.progressView(player, s, false, false);
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(15).getType());
      assertTrue(top().getItem(11).getItemMeta().getLore().toString().contains("Only the builder or staff"));
    }

    @Test
    void aFinishedBuildHasNoButton() {
      real.progressView(player, s, true, true);
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, top().getItem(15).getType());
      assertTrue(top().getItem(11).getItemMeta().getLore().toString().contains("Finished"));
    }

    @Test
    void theConfirmScreenHasYesAndABackBarrier() {
      real.confirmCancelView(player, s, b, true);
      var holder = (VFBHolder) top().getHolder();
      assertEquals(VFBGUI.CONFIRM_CANCEL, holder.getType());
      assertSame(b, holder.getBuild());
      assertEquals("confirm_cancel", action(top().getItem(11)));
      assertEquals(Material.BARRIER, top().getItem(15).getType());
      assertEquals("§aNo, keep building", top().getItem(15).getItemMeta().getDisplayName());
    }
  }
}
