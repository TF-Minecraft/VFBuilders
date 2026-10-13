package net.tfminecraft.vfbuilders.events;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;

public class BeginVehicleConstructionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player constructor;
    private final UUID constructorCharacterId;
    private final Blueprint blueprint;
    private final ActiveStation station;
    private final Location spawnLocation;
    private boolean cancelled;
    private boolean keepPlacement;

    public BeginVehicleConstructionEvent(
            Player constructor,
            Blueprint blueprint,
            ActiveStation station,
            Location spawnLocation) {
        this(constructor, null, blueprint, station, spawnLocation);
    }

    public BeginVehicleConstructionEvent(
            Player constructor,
            UUID constructorCharacterId,
            Blueprint blueprint,
            ActiveStation station,
            Location spawnLocation) {
        this.constructor = constructor;
        this.constructorCharacterId = constructorCharacterId;
        this.blueprint = blueprint;
        this.station = station;
        this.spawnLocation = spawnLocation;
        this.cancelled = false;
    }

    public Player getConstructor() {
        return constructor;
    }

    /**
     * The character the constructor is playing, recorded on the station if construction starts.
     * Null when the player has no current character session.
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

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Whether a cancelled event leaves the player's placement active, so another
     * left-click within the placement window fires this event again. Listeners use
     * this to ask for a confirming second click.
     */
    public boolean isKeepPlacement() {
        return keepPlacement;
    }

    public void setKeepPlacement(boolean keepPlacement) {
        this.keepPlacement = keepPlacement;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
