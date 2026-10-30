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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Database {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** Held while a snapshot file is written; package-visible so tests can hold the writer. */
    static final Object WRITE_LOCK = new Object();
    private static final AtomicLong snapshots = new AtomicLong();
    // Guarded by WRITE_LOCK: the newest snapshot that has started writing.
    private static long newestWrite;
    private static ExecutorService writer;

    /** The stations file, under the plugin's current data folder. */
    static Path path() {
        return file().toPath();
    }

    private static File file() {
        return new File(VFBuilders.plugin.getDataFolder(), "data/stations.json");
    }

    public static void saveStations(Map<Location, ActiveStation> stations) {
        saveStationsNow(stations);
    }

    /**
     * Writes the stations durably before returning. Callers that must know the state is on disk
     * (construction start, completion, cancellation, station removal, disable) use this.
     */
    public static boolean saveStationsNow(Map<Location, ActiveStation> stations) {
        try {
            return write(snapshot(stations));
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Snapshots the stations on the calling thread and writes them durably on the station writer
     * thread. A snapshot taken earlier than one already written is dropped, so a later synchronous
     * save is never overwritten by an older queued one.
     */
    public static CompletableFuture<Boolean> saveStationsLater(Map<Location, ActiveStation> stations) {
        Snapshot snapshot;
        try {
            snapshot = snapshot(stations);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> write(snapshot), writer());
    }

    /** Finishes queued writes and stops the writer thread; the next background save starts a new one. */
    public static void close() {
        close(Duration.ofSeconds(30));
    }

    static void close(Duration timeout) {
        ExecutorService current;
        synchronized (Database.class) {
            current = writer;
            writer = null;
        }
        if (current == null) return;
        current.shutdown();
        try {
            if (!current.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                VFBuilders.plugin.getLogger().warning(
                    "Station writer still busy after " + timeout.toSeconds() + "s; the last synchronous save stands.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static synchronized ExecutorService writer() {
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "VFBuilders station writer");
                thread.setDaemon(true);
                return thread;
            });
        }
        return writer;
    }

    private static Snapshot snapshot(Map<Location, ActiveStation> stations) {
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
        return new Snapshot(snapshots.incrementAndGet(), file(), dataList);
    }

    private static boolean write(Snapshot snapshot) {
        synchronized (WRITE_LOCK) {
            if (snapshot.sequence() < newestWrite) return true; // A newer snapshot already wrote.
            newestWrite = snapshot.sequence();
            return writeFile(snapshot.target(), snapshot.stations());
        }
    }

    private static boolean writeFile(File file, List<StationData> dataList) {
        try {
            var directory = file.toPath().getParent();
            Files.createDirectories(directory);
            var temporary = Files.createTempFile(directory, "stations-", ".tmp");
            boolean replaced = false;
            try {
                try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                    output.write(gson.toJson(dataList).getBytes(StandardCharsets.UTF_8));
                    output.getFD().sync();
                }
                Files.move(temporary, file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                replaced = true;
            } finally {
                if (!replaced) Files.deleteIfExists(temporary);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    // The target is resolved on the calling thread, so the writer thread never reads plugin state.
    private record Snapshot(long sequence, File target, List<StationData> stations) {}

    public static HashMap<Location, ActiveStation> loadStations() {
        HashMap<Location, ActiveStation> loaded = new HashMap<>();

        File file = file();
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

