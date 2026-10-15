package net.tfminecraft.vfbuilders.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import net.tfminecraft.vfbuilders.VFBuilders;
import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Station;
import net.tfminecraft.vfbuilders.loaders.StationLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Database {

    private static final File file = new File(VFBuilders.plugin.getDataFolder(), "data/stations.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void saveStations(Map<Location, ActiveStation> stations) {
        saveStationsNow(stations);
    }

    public static boolean saveStationsNow(Map<Location, ActiveStation> stations) {
        List<StationData> dataList = new ArrayList<>();

        for (ActiveStation station : stations.values()) {
            StationData data = new StationData();
            data.location = toSerializableLoc(station.getLocation());
            data.stationId = station.getStation().getId(); // Save station identifier
            data.spawnLocation = station.hasSpawnLocation() ? toSerializableLoc(station.getSpawnLocation()) : null;
            data.blueprintId = station.hasBlueprint() ? station.getBlueprint().getId() : null;
            data.timeLeft = station.getTimeLeft();
            data.outputUuid = station.getOutputUuid();
            if (station.hasBlueprint()) {
                data.constructorUuid = toText(station.getConstructorUuid());
                data.constructorCharacterId = toText(station.getConstructorCharacterId());
            }

            dataList.add(data);
        }

        try {
            var directory = file.toPath().getParent();
            Files.createDirectories(directory);
            var temporary = Files.createTempFile(directory, "stations-", ".tmp");
            try (Closeable cleanup = () -> Files.deleteIfExists(temporary)) {
                try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                    output.write(gson.toJson(dataList).getBytes(StandardCharsets.UTF_8));
                    output.getFD().sync();
                }
                Files.move(temporary, file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static HashMap<Location, ActiveStation> loadStations() {
        HashMap<Location, ActiveStation> loaded = new HashMap<>();

        if (!file.exists()) return loaded;

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<StationData>>(){}.getType();
            List<StationData> dataList = gson.fromJson(reader, listType);

            for (StationData data : dataList) {
                Location loc = toBukkitLoc(data.location);
                Station baseStation = StationLoader.getByString(data.stationId);
                if (baseStation == null) continue;

                ActiveStation station = new ActiveStation(
                    loc,
                    baseStation,
                    data.blueprintId,
                    data.timeLeft,
                    data.spawnLocation != null ? toBukkitLoc(data.spawnLocation) : null,
                    toUuid(data.constructorUuid),
                    // Stations saved before 2.2.0 have no character; SimpleFactions resolves the heir.
                    toUuid(data.constructorCharacterId)
                );
                station.setOutputUuid(data.outputUuid);

                loaded.put(loc, station);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return loaded;
    }

    // === Serializable Helpers ===
    private static String toText(UUID id) {
        return id != null ? id.toString() : null;
    }

    private static UUID toUuid(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static SerializableLoc toSerializableLoc(Location loc) {
        return new SerializableLoc(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static Location toBukkitLoc(SerializableLoc loc) {
        return new Location(Bukkit.getWorld(loc.world), loc.x, loc.y, loc.z);
    }

    private static class SerializableLoc {
        String world;
        int x, y, z;

        public SerializableLoc(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class StationData {
        SerializableLoc location;
        SerializableLoc spawnLocation;
        String stationId;
        String blueprintId;
        int timeLeft;
        String constructorUuid;
        String constructorCharacterId;
        String outputUuid;
    }
}

