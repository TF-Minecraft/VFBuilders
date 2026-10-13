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
    private final UUID constructorCharacterId;
    private final Blueprint blueprint;
    private final ActiveStation station;

    public VehicleConstructionCancelEvent(UUID constructorUuid, Blueprint blueprint, ActiveStation station) {
        this(constructorUuid, null, blueprint, station);
    }

    public VehicleConstructionCancelEvent(
            UUID constructorUuid, UUID constructorCharacterId, Blueprint blueprint, ActiveStation station) {
        this.constructorUuid = constructorUuid;
        this.constructorCharacterId = constructorCharacterId;
        this.blueprint = blueprint;
        this.station = station;
    }

    /** The account that started the project. Null for constructions saved before it was recorded. */
    public UUID getConstructorUuid() {
        return constructorUuid;
    }

    /**
     * The character the account was playing when the project started. Null when it had no
     * current character session, or for constructions saved before the character was recorded.
     */
    public UUID getConstructorCharacterId() {
        return constructorCharacterId;
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
