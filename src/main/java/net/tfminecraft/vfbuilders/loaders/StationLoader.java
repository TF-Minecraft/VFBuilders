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
import net.tfminecraft.vfbuilders.core.Station;

public class StationLoader implements LoaderInterface{
	public static HashMap<String, Station> map = new HashMap<>();
	
	public static HashMap<String, Station> get(){
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
			Station o = new Station(key, config.getConfigurationSection(key));
			map.put(key, o);
		}
	}

	public static Station getByString(String id) {
		if(map.containsKey(id)) return map.get(id);
		return null;
	}
    
}
