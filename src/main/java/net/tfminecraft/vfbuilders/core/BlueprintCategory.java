package net.tfminecraft.vfbuilders.core;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.vfbuilders.loaders.StationLoader;

public class BlueprintCategory {
    private String id;
    private ItemStack item;
    private Station station;

    private List<Blueprint> blueprints = new ArrayList<>();

    private String permission;

    
    public BlueprintCategory(String key, ConfigurationSection config) {
        id = key;
        if(config.isConfigurationSection("item")) item = TLibs.getItemAPI().getCreator().getItemFromConfig(config.getConfigurationSection("item"));
        else item = new ItemStack(Material.DIRT, 1);
        station = StationLoader.getByString(config.getString("station", "none"));
        permission = config.getString("permission", null);
    }

    public String getId() {
        return id;
    }

    public void addBlueprint(Blueprint b) {
        if(blueprints.contains(b)) return;
        blueprints.add(b);
    }

    public boolean hasPermission() {
        return permission != null;
    }

    public String getPermission() {
        return permission;
    }

    public ItemStack getItem() {
        return item;
    }

    public Station getStation() {
        return station;
    }

    public List<Blueprint> getBlueprints() {
        return blueprints;
    }
}
