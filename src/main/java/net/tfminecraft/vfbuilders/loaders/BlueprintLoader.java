package net.tfminecraft.vfbuilders.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.vfbuilders.core.Blueprint;

public class BlueprintLoader implements LoaderInterface{
	public static HashMap<String, Blueprint> map = new HashMap<>();
	
	public static HashMap<String, Blueprint> get(){
		return map;
	}
	
	@Override
	public void load(File configFile) {
		
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			Blueprint o = new Blueprint(key, config.getConfigurationSection(key));
			map.put(key, o);
		}
	}

	public static Blueprint getByString(String id) {
		if(map.containsKey(id)) return map.get(id);
		return null;
	}
    
}
