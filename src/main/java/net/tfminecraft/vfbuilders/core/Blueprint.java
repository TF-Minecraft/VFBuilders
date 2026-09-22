package net.tfminecraft.vfbuilders.core;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.utils.TimeFormatter;
import net.tfminecraft.vfbuilders.VFBuilders;
import net.tfminecraft.vfbuilders.loaders.CategoryLoader;
import net.tfminecraft.vehicleframework.VFLogger;
import net.tfminecraft.vehicleframework.loaders.VehicleLoader;
import net.tfminecraft.vehicleframework.vehicles.Vehicle;

public class Blueprint {
    private ItemAPI api = TLibs.getItemAPI();

    private String id;
    private BlueprintCategory category;
    private ItemStack item;
    private Vehicle vehicle;

    private int time;
    private HashMap<String, Integer> inputs = new HashMap<>();

    private String permission;

    public Blueprint(String key, ConfigurationSection config) {
        id = key;
        category = CategoryLoader.getByString(config.getString("category", "none"));
        if(category != null) {
            category.addBlueprint(this);
        }
        if(config.isConfigurationSection("item")) item = api.getCreator().getItemFromConfig(config.getConfigurationSection("item"));
        else item = new ItemStack(Material.DIRT, 1);
        time = parseTime(key, config);
        for(String s : config.getStringList("inputs")) {
            String input = s.split("\\s+")[0];
            Integer amount = 1;
            try {
                amount = Integer.parseInt(s.split("\\s+")[1]);
            } catch (Exception e) {
                VFLogger.log(VFBuilders.plugin, s+" is not a correctly formulated input for the blueprint "+key);
            }
            inputs.put(input, amount);
        }
        permission = config.getString("permission", null);
        vehicle = VehicleLoader.getByString(config.getString("vehicle", "none"));
    }

    public String getId() {
        return id;
    }

    public boolean hasVehicle() {
        return vehicle != null;
    }

    public Vehicle getVehicle() {
        return vehicle;
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

    public int getTime() {
        return time;
    }

    private static int parseTime(String key, ConfigurationSection config) {
        Object raw = config.get("time");
        if (raw == null) {
            return 10;
        }
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return TimeFormatter.parseSeconds(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            VFLogger.log(VFBuilders.plugin, raw + " is not a valid time for the blueprint " + key);
            return 10;
        }
    }

    public HashMap<String, Integer> getInputs() {
        return inputs;
    }

    @SuppressWarnings("unchecked")
    public boolean hasInputs(Player p) {
        HashMap<String, Integer> items = (HashMap<String, Integer>) inputs.clone();
        for(ItemStack i : p.getInventory().getContents()) {
            if (i == null || i.getType() == Material.AIR) continue;
            String path = api.getChecker().getAsStringPath(i);
            if(items.containsKey(path)) {
                int current = items.get(path);
                current-=i.getAmount();
                if(current > 0) {
                    items.put(path, current);
                } else {
                    items.remove(path);
                }
            }
            // Break early if we've hit the target
            if (items.isEmpty()) break;
        }
        return items.size() == 0;
    }

    @SuppressWarnings("unchecked")
    public void takeInputs(Player p) {
        HashMap<String, Integer> items = (HashMap<String, Integer>) inputs.clone();

        for (ItemStack i : p.getInventory().getContents()) {
            if (i == null || i.getType() == Material.AIR) continue;

            String path = api.getChecker().getAsStringPath(i);
            if (!items.containsKey(path)) continue;

            int required = items.get(path);
            int available = i.getAmount();

            if (available >= required) {
                i.setAmount(available - required); // Decrease the stack
                items.remove(path); // All required amount taken
            } else {
                i.setAmount(0); // Remove entire stack
                items.put(path, required - available); // Still need more of this item
            }

            // Break early if we've taken all required items
            if (items.isEmpty()) break;
        }

        // Optionally update inventory to reflect changes
        p.updateInventory();
    }

    public void drop(Location loc) {
        // Play a cool burst sound and particles
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0.5, 1, 0.5), 15, 0.3, 0.3, 0.3, 0.01);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0.5, 1, 0.5), 20, 0.2, 0.2, 0.2, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_BARREL_CLOSE, 1f, 1.3f);

        // Drop all items
        for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
            String path = entry.getKey();
            int amount = entry.getValue();

            ItemStack item = api.getCreator().getItemFromPath(path);
            item.setAmount(amount);

            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), item);
        }
    }
}
