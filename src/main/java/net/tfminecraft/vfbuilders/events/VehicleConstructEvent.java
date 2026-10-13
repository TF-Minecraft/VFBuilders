package net.tfminecraft.vfbuilders.events;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public class VehicleConstructEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID constructorUuid;
    private final UUID constructorCharacterId;
    private final Player constructor;
    private final ActiveVehicle vehicle;
    private final Blueprint blueprint;
    private final Location spawnLocation;
    private final ActiveStation station;

    public VehicleConstructEvent(
            UUID constructorUuid,
            ActiveVehicle vehicle,
            Blueprint blueprint,
            Location spawnLocation,
            ActiveStation station) {
        this(constructorUuid, null, vehicle, blueprint, spawnLocation, station);
    }

    public VehicleConstructEvent(
            UUID constructorUuid,
            UUID constructorCharacterId,
            ActiveVehicle vehicle,
            Blueprint blueprint,
            Location spawnLocation,
            ActiveStation station) {
        this.constructorUuid = constructorUuid;
        this.constructorCharacterId = constructorCharacterId;
        this.constructor = constructorUuid != null ? Bukkit.getPlayer(constructorUuid) : null;
        this.vehicle = vehicle;
        this.blueprint = blueprint;
        this.spawnLocation = spawnLocation;
        this.station = station;
    }

    /** The account that started the project. */
    public UUID getConstructorUuid() {
        return constructorUuid;
    }

    /**
     * The character the account was playing when the project started. Null when it had no
     * current character session, or for projects saved before the character was recorded.
     */
    public UUID getConstructorCharacterId() {
        return constructorCharacterId;
    }

    public Player getConstructor() {
        return constructor;
    }

    public ActiveVehicle getVehicle() {
        return vehicle;
    }

    public Blueprint getBlueprint() {
        return blueprint;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public ActiveStation getStation() {
        return station;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
