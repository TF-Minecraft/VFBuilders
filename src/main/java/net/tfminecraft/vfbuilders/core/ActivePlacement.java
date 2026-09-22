package net.tfminecraft.vfbuilders.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ActivePlacement {
    private final Player player;
    private final ActiveStation station;
    private final Blueprint blueprint;
    private Location finalSpawnLocation;

    public ActivePlacement(Player player, ActiveStation station, Blueprint blueprint) {
        this.player = player;
        this.station = station;
        this.blueprint = blueprint;
    }

    public Player getPlayer() { return player; }
    public ActiveStation getStation() { return station; }
    public Blueprint getBlueprint() { return blueprint; }

    public Location getFinalSpawnLocation() { return finalSpawnLocation; }
    public void setFinalSpawnLocation(Location loc) { this.finalSpawnLocation = loc; }
}

