package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.vehicleframework.VFLogger;
import net.tfminecraft.vfbuilders.api.*;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.enums.*;
import net.tfminecraft.vfbuilders.events.*;
import net.tfminecraft.vfbuilders.holders.*;
import net.tfminecraft.vfbuilders.loaders.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class DomainTest {
  ServerMock server;
  ItemAPI api;
  MockedStatic<TLibs> libs;
  MockedStatic<VFLogger> logger;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getItemAPI).thenReturn(api);
    logger = mockStatic(VFLogger.class);
    StationLoader.get().clear();
    CategoryLoader.get().clear();
    BlueprintLoader.get().clear();
  }

  @AfterEach
  void teardown() {
    logger.close();
    libs.close();
    MockBukkit.unmock();
  }

  YamlConfiguration yaml(String text) throws Exception {
    var y = new YamlConfiguration();
    y.loadFromString(text);
    return y;
  }

  @Test
  void stationCategoryAndBlueprintDefinitions() throws Exception {
    var station = new Station("forge", yaml("block: v.stone"));
    StationLoader.get().put("forge", station);
    assertEquals("forge", station.getId());
    assertEquals("v.stone", station.getBlock());
    assertEquals("v.bedrock", new Station("default", yaml("")).getBlock());
    var category = new BlueprintCategory("boats", yaml("station: forge\npermission: boats"));
    CategoryLoader.get().put("boats", category);
    assertSame(station, category.getStation());
    assertEquals("boats", category.getId());
    assertEquals(Material.DIRT, category.getItem().getType());
    assertTrue(category.hasPermission());
    assertEquals("boats", category.getPermission());
    var b =
        new Blueprint(
            "boat",
            yaml("category: boats\ntime: 17\npermission: build\ninputs: [v.iron 4, malformed]"));
    assertEquals("boat", b.getId());
    assertEquals(17, b.getTime());
    assertTrue(b.hasPermission());
    assertEquals("build", b.getPermission());
    assertFalse(b.hasVehicle());
    assertNull(b.getVehicle());
    assertEquals(Material.DIRT, b.getItem().getType());
    assertEquals(Map.of("v.iron", 4, "malformed", 1), b.getInputs());
    category.addBlueprint(b);
    assertEquals(List.of(b), category.getBlueprints());
    assertNull(StationLoader.getByString("missing"));
    assertSame(station, StationLoader.getByString("forge"));
    assertSame(category, CategoryLoader.getByString("boats"));
    assertNull(CategoryLoader.getByString("missing"));
    BlueprintLoader.get().put("boat", b);
    assertSame(b, BlueprintLoader.getByString("boat"));
    assertNull(BlueprintLoader.getByString("missing"));
  }

  @Test
  void timeDefaultsInvalidAndConfiguredIcons() throws Exception {
    assertEquals(10, new Blueprint("b", yaml("")).getTime());
    assertEquals(0, new Blueprint("b", yaml("time: -10")).getTime());
    assertEquals(120, new Blueprint("b", yaml("time: 2m")).getTime());
    assertEquals(10, new Blueprint("b", yaml("time: nonsense")).getTime());
    var icon = new ItemStack(Material.PAPER);
    when(api.getCreator().getItemFromConfig(any())).thenReturn(icon);
    var b = new Blueprint("b", yaml("item: {type: paper}"));
    assertSame(icon, b.getItem());
    assertFalse(b.hasPermission());
    assertNull(b.getPermission());
    var c = new BlueprintCategory("c", yaml("item: {type: paper}"));
    assertSame(icon, c.getItem());
    assertFalse(c.hasPermission());
    assertNull(c.getPermission());
    assertNull(c.getStation());
  }

  @Test
  void materialAccountingConsumesAcrossStacksAndLeavesUnrelatedItems() throws Exception {
    var player = server.addPlayer();
    var b = new Blueprint("b", yaml("inputs: [v.iron 4, v.gold 2]"));
    when(api.getChecker().getAsStringPath(any()))
        .thenAnswer(
            a ->
                "v."
                    + ((ItemStack) a.getArgument(0))
                        .getType()
                        .name()
                        .toLowerCase()
                        .replace("_ingot", ""));
    player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 2));
    player.getInventory().setItem(1, new ItemStack(Material.STONE, 3));
    player.getInventory().setItem(2, new ItemStack(Material.IRON_INGOT, 5));
    assertFalse(b.hasInputs(player));
    player.getInventory().setItem(3, new ItemStack(Material.GOLD_INGOT, 2));
    assertTrue(b.hasInputs(player));
    b.takeInputs(player);
    assertEquals(3, player.getInventory().getItem(1).getAmount());
    assertEquals(3, player.getInventory().getItem(2).getAmount());
    assertTrue(
        player.getInventory().getItem(0) == null
            || player.getInventory().getItem(0).getAmount() == 0);
    assertFalse(b.hasInputs(player));
    var free = new Blueprint("free", yaml(""));
    assertTrue(free.hasInputs(player));
    free.takeInputs(player);
    player.getInventory().clear();
    assertTrue(free.hasInputs(player));
    b.takeInputs(player);
    assertTrue(player.getInventory().isEmpty());
  }

  @Test
  void cancellationDropsConfiguredMaterials() throws Exception {
    var b = new Blueprint("b", yaml("inputs: [v.iron 4]"));
    when(api.getCreator().getItemFromPath("v.iron")).thenReturn(new ItemStack(Material.IRON_INGOT));
    World world = mock(World.class);
    var loc = new Location(world, 1, 2, 3);
    b.drop(loc);
    verify(world)
        .dropItemNaturally(
            eq(new Location(world, 1.5, 3, 3.5)),
            argThat(i -> i.getType() == Material.IRON_INGOT && i.getAmount() == 4));
    assertEquals(1, loc.getX());
  }

  @Test
  void eventsExposeConstructionIdentityAndCancellationPolicy() {
    var player = server.addPlayer();
    var station = new ActiveStation(player.getLocation(), null);
    var b = mock(Blueprint.class);
    var loc = player.getLocation();
    var e = new BeginVehicleConstructionEvent(player, b, station, loc);
    assertSame(player, e.getConstructor());
    assertSame(b, e.getBlueprint());
    assertSame(station, e.getStation());
    assertSame(loc, e.getSpawnLocation());
    assertFalse(e.isCancelled());
    assertFalse(e.isKeepPlacement());
    e.setCancelled(true);
    e.setKeepPlacement(true);
    assertTrue(e.isCancelled());
    assertTrue(e.isKeepPlacement());
    assertSame(BeginVehicleConstructionEvent.getHandlerList(), e.getHandlers());
    var complete = new VehicleConstructEvent(player.getUniqueId(), null, b, loc, station);
    assertSame(player, complete.getConstructor());
    assertEquals(player.getUniqueId(), complete.getConstructorUuid());
    assertNull(complete.getVehicle());
    assertSame(b, complete.getBlueprint());
    assertSame(station, complete.getStation());
    assertSame(loc, complete.getSpawnLocation());
    assertSame(VehicleConstructEvent.getHandlerList(), complete.getHandlers());
    assertNull(new VehicleConstructEvent(null, null, b, loc, station).getConstructor());
    var cancel = new VehicleConstructionCancelEvent(player.getUniqueId(), b, station);
    assertEquals(player.getUniqueId(), cancel.getConstructorUuid());
    assertSame(b, cancel.getBlueprint());
    assertSame(station, cancel.getStation());
    assertSame(VehicleConstructionCancelEvent.getHandlerList(), cancel.getHandlers());
    var placement = new ActivePlacement(player, station, b);
    assertSame(player, placement.getPlayer());
    assertSame(station, placement.getStation());
    assertSame(b, placement.getBlueprint());
    assertNull(placement.getFinalSpawnLocation());
    placement.setFinalSpawnLocation(loc);
    assertSame(loc, placement.getFinalSpawnLocation());
    var holder = new VFBHolder(station, VFBGUI.BLUEPRINT);
    assertSame(station, holder.getStation());
    assertEquals(VFBGUI.BLUEPRINT, holder.getType());
    assertNull(holder.getInventory());
    new Cache();
  }

  @Test
  void freezeProvidersFailIndependentlyAndFirstReasonWins() {
    assertNull(ConstructionFreezes.reason(null));
    var uuid = UUID.randomUUID();
    assertNull(ConstructionFreezes.reason(uuid));
    var plugin = MockBukkit.createMockPlugin();
    ConstructionFreeze broken =
        id -> {
          throw new IllegalStateException("failure");
        };
    ConstructionFreeze pass = id -> null;
    ConstructionFreeze paused = id -> "under siege";
    server
        .getServicesManager()
        .register(ConstructionFreeze.class, broken, plugin, ServicePriority.Highest);
    server
        .getServicesManager()
        .register(ConstructionFreeze.class, pass, plugin, ServicePriority.High);
    assertNull(ConstructionFreezes.reason(uuid));
    server
        .getServicesManager()
        .register(ConstructionFreeze.class, paused, plugin, ServicePriority.Normal);
    assertEquals("under siege", ConstructionFreezes.reason(uuid));
  }

  @Test
  void airSlotsAreIgnoredAndKnownVehicleIsResolved() throws Exception {
    var player = mock(Player.class);
    var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getContents()).thenReturn(new ItemStack[] {new ItemStack(Material.AIR)});
    var b = new Blueprint("b", yaml("inputs: [v.iron 1]"));
    assertFalse(b.hasInputs(player));
    b.takeInputs(player);
    verify(player).updateInventory();
    try (var loader = mockStatic(net.tfminecraft.vehicleframework.loaders.VehicleLoader.class)) {
      var vehicle = mock(net.tfminecraft.vehicleframework.vehicles.Vehicle.class);
      loader
          .when(() -> net.tfminecraft.vehicleframework.loaders.VehicleLoader.getByString("boat"))
          .thenReturn(vehicle);
      var known = new Blueprint("b", yaml("vehicle: boat"));
      assertTrue(known.hasVehicle());
      assertSame(vehicle, known.getVehicle());
    }
  }
}
