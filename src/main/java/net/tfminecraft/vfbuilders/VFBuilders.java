package net.tfminecraft.vfbuilders;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.vfbuilders.loaders.BlueprintLoader;
import net.tfminecraft.vfbuilders.loaders.CategoryLoader;
import net.tfminecraft.vfbuilders.loaders.ConfigLoader;
import net.tfminecraft.vfbuilders.loaders.StationLoader;
import net.tfminecraft.vfbuilders.managers.CommandManager;
import net.tfminecraft.vfbuilders.managers.StationManager;
import net.tfminecraft.vehicleframework.VFLogger;

public class VFBuilders extends JavaPlugin {
    public static VFBuilders plugin;

	//Managers
	private final CommandManager commandManager = new CommandManager();
	private final StationManager stationManager = new StationManager();

	//Loaders
	private final ConfigLoader configLoader = new ConfigLoader();
	private final StationLoader stationLoader = new StationLoader();
	private final BlueprintLoader blueprintLoader = new BlueprintLoader();
	private final CategoryLoader categoryLoader = new CategoryLoader();

    @Override
    public void onEnable() {
        plugin = this;
		VFLogger.info(plugin, "Loading...");
		createFolders();
		createConfigs();
		loadConfigs();
		registerListeners();
		startManagers();
    }

    @Override
	public void onDisable() {
		stationManager.stop();
	}
	public void registerListeners() {
		getServer().getPluginManager().registerEvents(stationManager, this);
		
		getCommand(commandManager.cmd1).setExecutor(commandManager);
		getCommand(commandManager.cmd1).setTabCompleter(commandManager);
	}

	public void reload() {
		stationManager.rebindAfterReload(() -> {
			BlueprintLoader.get().clear();
			CategoryLoader.get().clear();
			StationLoader.get().clear();
			loadConfigs();
		});
	}
	public void startManagers() {
		stationManager.start();
	}
	public void createFolders() {
		if (!getDataFolder().exists()) getDataFolder().mkdir();
		File subFolder = new File(getDataFolder(), "data");
		if(!subFolder.exists()) subFolder.mkdir();
		subFolder = new File(getDataFolder(), "blueprints");
		if(!subFolder.exists()) subFolder.mkdir();
	}
	public void loadConfigs() {
		configLoader.load(new File(getDataFolder(), "config.yml"));
		stationLoader.load(new File(getDataFolder(), "stations.yml"));
		categoryLoader.load(new File(getDataFolder(), "categories.yml"));
		File folder = new File(getDataFolder(), "blueprints");
		if (!folder.exists() || !folder.isDirectory()) {
			return;
		}
		File[] files = folder.listFiles();
		if (files == null) {
			return;
		}
		VFLogger.info(this, "Loading blueprints...");
		for (File file : files) {
			if (file != null && file.isFile()) {
				blueprintLoader.load(file);
			}
		}
	}
	
	public void createConfigs() {
		String[] files = {
				"config.yml",
                "categories.yml",
                "stations.yml"
				};
		for(String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
	        if (!newConfigFile.exists()) {
	        	newConfigFile.getParentFile().mkdirs();
	            saveResource(s, false);
	        }
		}
	}

}
