package com.ruinscraft.countries;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

public class DynmapCountries extends JavaPlugin {

	private Plugin dynmap;
	private DynmapAPI api;
	private MarkerAPI markerapi;
	private MarkerSet markerSet;

	@Override
	public void onEnable() {
		/* Get dynmap */
		dynmap = getServer().getPluginManager().getPlugin("dynmap");
		if (dynmap == null) {
			this.getLogger().severe("Need Dynmap!!!");
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		api = (DynmapAPI) dynmap; /* Get API */

		/* If both enabled, activate */
		if (dynmap.isEnabled()) {
			activate();
		}
	}

	private void activate() {
		markerapi = api.getMarkerAPI();
		if(markerapi == null) {
			this.getLogger().severe("Error loading dynmap marker API!");
			return;
		}

		FileConfiguration cfg = getConfig();
		cfg.options().copyDefaults(true);   /* Load defaults, if needed */
		this.saveConfig();  /* Save updates, if needed */

		/* Now, add marker set for mobs (make it transient) */
		markerSet = markerapi.getMarkerSet("countries.markerset");
		if(markerSet == null)
			markerSet = markerapi.createMarkerSet("countries.markerset", cfg.getString("Countries"), null, false);
		else
			markerSet.setMarkerSetLabel(cfg.getString("layer.name", "Countries"));
		if(markerSet == null) {
			this.getLogger().severe("Error creating marker set");
			return;
		}

		int minzoom = cfg.getInt("layer.minzoom", 0);
		if (minzoom > 0) markerSet.setMinZoom(minzoom);
		markerSet.setLayerPriority(cfg.getInt("layer.layerprio", 12));
		markerSet.setHideByDefault(cfg.getBoolean("layer.hidebydefault", true));

		this.getLogger().info("version " + this.getDescription().getVersion() + " is activated");
	}

}