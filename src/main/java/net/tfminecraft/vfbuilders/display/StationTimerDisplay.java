package net.tfminecraft.vfbuilders.display;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.utils.TimeFormatter;
import net.tfminecraft.vfbuilders.VFBuilders;

public final class StationTimerDisplay {

	private static NamespacedKey stationTimerKey() {
		return new NamespacedKey(VFBuilders.plugin, "station_timer");
	}
	private static final double DEFAULT_PURGE_RADIUS = 1.5;

	private StationTimerDisplay() {}

	public static String blockKey(Location stationBlock) {
		if (stationBlock == null || stationBlock.getWorld() == null) {
			return null;
		}
		return stationBlock.getWorld().getName()
				+ ':' + stationBlock.getBlockX()
				+ ':' + stationBlock.getBlockY()
				+ ':' + stationBlock.getBlockZ();
	}

	public static Location displayAnchor(Location stationBlock) {
		if (stationBlock == null) {
			return null;
		}
		return stationBlock.clone().add(0.5, 1.35, 0.5);
	}

	public static String formatText(String vehicleName, int timeLeftSeconds) {
		String name = vehicleName == null ? "Vehicle" : vehicleName;
		return "§eConstructing §6" + name
				+ "\n§7Time: §f" + TimeFormatter.formatTime(timeLeftSeconds);
	}

	public static TextDisplay spawn(World world, Location stationBlock, String text) {
		if (world == null || stationBlock == null || text == null) {
			return null;
		}
		if (!world.equals(stationBlock.getWorld())) {
			return null;
		}
		Location anchor = displayAnchor(stationBlock);
		if (anchor == null) {
			return null;
		}
		String key = blockKey(stationBlock);
		if (key == null) {
			return null;
		}
		return world.spawn(anchor, TextDisplay.class, display -> apply(display, anchor, text, key));
	}

	public static void update(TextDisplay display, Location stationBlock, String text) {
		if (display == null || display.isDead() || stationBlock == null || text == null) {
			return;
		}
		Location anchor = displayAnchor(stationBlock);
		if (anchor == null || anchor.getWorld() == null) {
			return;
		}
		display.teleport(anchor);
		display.setText(text);
	}

	public static void remove(TextDisplay display) {
		if (display != null && !display.isDead()) {
			display.remove();
		}
	}

	public static void removeById(UUID entityId) {
		if (entityId == null) {
			return;
		}
		Entity entity = Bukkit.getEntity(entityId);
		if (entity instanceof TextDisplay textDisplay) {
			remove(textDisplay);
		}
	}

	public static void purgeTaggedInChunk(World world, int chunkX, int chunkZ) {
		if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
			return;
		}
		Chunk chunk = world.getChunkAt(chunkX, chunkZ);
		for (Entity entity : chunk.getEntities()) {
			if (hasStationTimerTag(entity)) {
				entity.remove();
			}
		}
	}

	public static void purgeAtStationBlock(Location stationBlock, double radius) {
		if (stationBlock == null || stationBlock.getWorld() == null) {
			return;
		}
		String key = blockKey(stationBlock);
		if (key == null) {
			return;
		}
		Location anchor = displayAnchor(stationBlock);
		if (anchor == null) {
			return;
		}
		World world = stationBlock.getWorld();
		int chunkX = stationBlock.getBlockX() >> 4;
		int chunkZ = stationBlock.getBlockZ() >> 4;
		if (!world.isChunkLoaded(chunkX, chunkZ)) {
			return;
		}
		double radiusSq = radius * radius;
		for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
			if (!(entity instanceof TextDisplay)) {
				continue;
			}
			if (!key.equals(getStationTimerTag(entity))) {
				continue;
			}
			if (entity.getLocation().distanceSquared(anchor) <= radiusSq) {
				entity.remove();
			}
		}
	}

	public static void purgeAtStationBlock(Location stationBlock) {
		purgeAtStationBlock(stationBlock, DEFAULT_PURGE_RADIUS);
	}

	public static void purgeLegacyArmorStands(Location stationBlock, double radius) {
		if (stationBlock == null || stationBlock.getWorld() == null) {
			return;
		}
		Location anchor = displayAnchor(stationBlock);
		if (anchor == null) {
			return;
		}
		World world = stationBlock.getWorld();
		int chunkX = stationBlock.getBlockX() >> 4;
		int chunkZ = stationBlock.getBlockZ() >> 4;
		if (!world.isChunkLoaded(chunkX, chunkZ)) {
			return;
		}
		double radiusSq = radius * radius;
		for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
			if (!(entity instanceof ArmorStand stand)) {
				continue;
			}
			if (stand.isVisible() || !stand.isMarker()) {
				continue;
			}
			if (stand.getLocation().distanceSquared(anchor) <= radiusSq) {
				stand.remove();
			}
		}
	}

	public static void purgeLegacyArmorStands(Location stationBlock) {
		purgeLegacyArmorStands(stationBlock, DEFAULT_PURGE_RADIUS);
	}

	private static void apply(TextDisplay display, Location anchor, String text, String blockKey) {
		display.setText(text);
		display.setBillboard(Display.Billboard.CENTER);
		display.setAlignment(TextDisplay.TextAlignment.CENTER);
		display.setShadowed(true);
		display.setSeeThrough(false);
		display.setGravity(false);
		display.setInvulnerable(true);
		display.setPersistent(false);
		display.teleport(anchor);
		display.getPersistentDataContainer().set(stationTimerKey(), PersistentDataType.STRING, blockKey);
	}

	private static boolean hasStationTimerTag(Entity entity) {
		return getStationTimerTag(entity) != null;
	}

	private static String getStationTimerTag(Entity entity) {
		if (entity == null) {
			return null;
		}
		PersistentDataContainer container = entity.getPersistentDataContainer();
		return container.get(stationTimerKey(), PersistentDataType.STRING);
	}
}
