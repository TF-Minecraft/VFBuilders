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
import java.util.*;

public class Database {

    private static final File file = new File(VFBuilders.plugin.getDataFolder(), "data/stations.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void saveStations(Map<Location, ActiveStation> stations) {
        List<StationData> dataList = new ArrayList<>();

        for (ActiveStation station : stations.values()) {
            station.removeHolograms();
            StationData data = new StationData();
            data.location = toSerializableLoc(station.getLocation());
            data.stationId = station.getStation().getId(); // Save station identifier
            data.spawnLocation = station.hasSpawnLocation() ? toSerializableLoc(station.getSpawnLocation()) : null;
            data.blueprintId = station.hasBlueprint() ? station.getBlueprint().getId() : null;
            data.timeLeft = station.getTimeLeft();
            if (station.hasBlueprint() && station.getConstructorUuid() != null) {
                data.constructorUuid = station.getConstructorUuid().toString();
            }

            dataList.add(data);
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(dataList, writer);
        } catch (IOException e) {
            e.printStackTrace();
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

                UUID constructorUuid = null;
                if (data.constructorUuid != null && !data.constructorUuid.isEmpty()) {
                    try {
                        constructorUuid = UUID.fromString(data.constructorUuid);
                    } catch (IllegalArgumentException ignored) {
                        constructorUuid = null;
                    }
                }

                ActiveStation station = new ActiveStation(
                    loc,
                    baseStation,
                    data.blueprintId,
                    data.timeLeft,
                    data.spawnLocation != null ? toBukkitLoc(data.spawnLocation) : null,
                    constructorUuid
                );

                loaded.put(loc, station);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return loaded;
    }

    // === Serializable Helpers ===
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
    }
}

