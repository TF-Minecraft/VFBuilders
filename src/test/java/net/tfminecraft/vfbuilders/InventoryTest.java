package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.vehicleframework.vehicles.Vehicle;
import net.tfminecraft.vehicleframework.vehicles.component.VehicleComponent;
import net.tfminecraft.vehicleframework.weapons.Weapon;
import net.tfminecraft.vfbuilders.core.*;
import net.tfminecraft.vfbuilders.loaders.*;
import net.tfminecraft.vfbuilders.managers.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class InventoryTest {
  ServerMock server;
  Player player;
  ActiveStation station;
  Station definition;
  InventoryManager manager;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    VFBuilders.plugin = mock(VFBuilders.class);
    when(VFBuilders.plugin.namespace()).thenReturn("vfbuilders");
    when(VFBuilders.plugin.getServer()).thenReturn(server);
    definition = mock(Station.class);
    station = new ActiveStation(player.getLocation(), definition);
    manager = new InventoryManager();
    CategoryLoader.get().clear();
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
    VFBuilders.plugin = null;
  }

  BlueprintCategory category(String id, Station base, boolean perm) {
    var cat = mock(BlueprintCategory.class);
    when(cat.getId()).thenReturn(id);
    when(cat.getStation()).thenReturn(base);
    when(cat.getItem()).thenReturn(new ItemStack(Material.PAPER));
    when(cat.hasPermission()).thenReturn(perm);
    when(cat.getPermission()).thenReturn("boats");
    CategoryLoader.get().put(id, cat);
    return cat;
  }

  Blueprint blueprint(String id, boolean perm, boolean vehicle) {
    var b = mock(Blueprint.class);
    when(b.getId()).thenReturn(id);
    when(b.getItem()).thenReturn(new ItemStack(Material.PAPER));
    when(b.hasPermission()).thenReturn(perm);
    when(b.getPermission()).thenReturn("boats");
    when(b.hasVehicle()).thenReturn(vehicle);
    return b;
  }

  @Test
  void categoriesWithPlainIconsDoNotCrashAndRespectStationAndPermission() {
    var cat = category("boats", definition, false);
    category("other", mock(Station.class), false);
    category("locked", definition, true);
    var free = blueprint("free", false, false);
    var gated = blueprint("locked", true, false);
    when(cat.getBlueprints()).thenReturn(List.of(free, gated));
    assertDoesNotThrow(() -> manager.categoryView(null, player, station, true));
    var inv = player.getOpenInventory().getTopInventory();
    assertEquals(
        "boats",
        inv.getItem(0)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(
                new NamespacedKey(VFBuilders.plugin, "vfb_category_id"),
                PersistentDataType.STRING));
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(1).getType());
    assertTrue(inv.getItem(0).getItemMeta().getLore().toString().contains("1"));
    var icon = cat.getItem();
    var meta = icon.getItemMeta();
    meta.setLore(List.of("Original"));
    icon.setItemMeta(meta);
    player.setOp(true);
    manager.categoryView(server.createInventory(null, 27), player, station, false);
  }

  @Test
  void blueprintsWithPlainIconsRenderVehicleAndIngredientDetails() {
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      var cat = category("boats", definition, false);
      var b = blueprint("boat", false, true);
      var invalid = blueprint("bad", false, false);
      var locked = blueprint("locked", true, true);
      when(cat.getBlueprints()).thenReturn(List.of(b, invalid, locked));
      var v = mock(Vehicle.class, RETURNS_DEEP_STUBS);
      when(v.getName()).thenReturn("Boat");
      when(b.getVehicle()).thenReturn(v);
      var component = mock(VehicleComponent.class, RETURNS_DEEP_STUBS);
      when(component.getAlias()).thenReturn("Hull");
      when(v.getComponentHandler().getComponents()).thenReturn(new ArrayList<>(List.of(component)));
      when(b.getInputs()).thenReturn(new HashMap<>(Map.of("v.iron", 3, "v.gold", 1)));
      var named = new ItemStack(Material.GOLD_INGOT);
      var meta = named.getItemMeta();
      meta.setDisplayName("Special Gold");
      named.setItemMeta(meta);
      when(api.getCreator().getItemFromPath("v.gold")).thenReturn(named);
      when(api.getCreator().getItemFromPath("v.iron"))
          .thenReturn(new ItemStack(Material.IRON_INGOT));
      assertDoesNotThrow(() -> manager.blueprintView(null, player, cat, station, true));
      var inv = player.getOpenInventory().getTopInventory();
      assertEquals("Boat", inv.getItem(0).getItemMeta().getDisplayName());
      assertEquals(Material.BARRIER, inv.getItem(26).getType());
      assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(1).getType());
      assertTrue(inv.getItem(0).getItemMeta().getLore().toString().contains("Special Gold"));
      var weapon = mock(Weapon.class, RETURNS_DEEP_STUBS);
      when(v.getWeapons()).thenReturn(new ArrayList<>(List.of(weapon)));
      when(weapon.getName()).thenReturn("Cannon");
      var icon = b.getItem();
      var original = icon.getItemMeta();
      original.setLore(List.of("Original"));
      icon.setItemMeta(original);
      player.setOp(true);
      when(locked.getVehicle()).thenReturn(v);
      manager.blueprintView(server.createInventory(null, 27), player, cat, station, false);
    }
  }
}
