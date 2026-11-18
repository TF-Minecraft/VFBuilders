package net.tfminecraft.vfbuilders.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.enums.VFBGUI;

public class VFBHolder implements InventoryHolder {
    private final ActiveStation station;
    private final VFBGUI type;
    private final Blueprint build;

    public VFBHolder(ActiveStation station, VFBGUI type) {
        this(station, type, null);
    }

    /** {@code build} is the blueprint the station was building when this menu opened. */
    public VFBHolder(ActiveStation station, VFBGUI type, Blueprint build) {
        this.station = station;
        this.type = type;
        this.build = build;
    }

    public ActiveStation getStation() {
        return station;
    }

    public VFBGUI getType() {
        return type;
    }

    public Blueprint getBuild() {
        return build;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used in this case
    }
}
