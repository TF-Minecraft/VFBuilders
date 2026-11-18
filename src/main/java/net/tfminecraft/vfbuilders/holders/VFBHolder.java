package net.tfminecraft.vfbuilders.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.vfbuilders.core.ActiveStation;
import net.tfminecraft.vfbuilders.enums.VFBGUI;

public class VFBHolder implements InventoryHolder {
    private final ActiveStation station;
    private final VFBGUI type;
    private final long buildId;

    public VFBHolder(ActiveStation station, VFBGUI type) {
        this(station, type, -1);
    }

    /** {@code buildId} is the station's build id when this menu opened; -1 for menus without a build. */
    public VFBHolder(ActiveStation station, VFBGUI type, long buildId) {
        this.station = station;
        this.type = type;
        this.buildId = buildId;
    }

    public ActiveStation getStation() {
        return station;
    }

    public VFBGUI getType() {
        return type;
    }

    public long getBuildId() {
        return buildId;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used in this case
    }
}
