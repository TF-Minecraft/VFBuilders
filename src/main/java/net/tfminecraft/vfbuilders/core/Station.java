package net.tfminecraft.vfbuilders.core;

import org.bukkit.configuration.ConfigurationSection;

public class Station {
    private String id;
    private String block;

    public Station(String key, ConfigurationSection config) {
        id = key;
        block = config.getString("block", "v.bedrock");
    }

    public String getId() {
        return id;
    }

    public String getBlock() {
        return block;
    }
}
