package net.tfminecraft.vfbuilders.events;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;

/**
 * Fired when a started construction is abandoned, after its materials are dropped back
 * at the station (the station was removed, or its blueprint disappeared on reload).
 */
public class VehicleConstructionCancelEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID constructorUuid;
    private final Blueprint blueprint;
    private final ActiveStation station;

    public VehicleConstructionCancelEvent(UUID constructorUuid, Blueprint blueprint, ActiveStation station) {
        this.constructorUuid = constructorUuid;
        this.blueprint = blueprint;
        this.station = station;
    }

    /** Null for constructions saved before the constructor was recorded. */
    public UUID getConstructorUuid() {
        return constructorUuid;
    }

    public Blueprint getBlueprint() {
        return blueprint;
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
