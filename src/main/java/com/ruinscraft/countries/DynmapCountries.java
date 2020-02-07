package com.ruinscraft.countries;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

public class DynmapCountries extends JavaPlugin {

	private Plugin dynmap;
	private DynmapAPI api;
	private MarkerAPI markerapi;
	private MarkerSet markerSet;

	private SimpleFeatureSource featureSource;

	@Override
	public void onEnable() {
		/* Get dynmap */
		dynmap = getServer().getPluginManager().getPlugin("dynmap");
		if (dynmap == null) {
			this.getLogger().severe("Need Dynmap!!!");
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		this.getLogger().info("enabled woo");

		api = (DynmapAPI) dynmap; /* Get API */

		/* If both enabled, activate */
		if (dynmap.isEnabled()) {
			activate();
		}
	}

	private void activate() {
		this.getLogger().info("woo");
		markerapi = api.getMarkerAPI();
		if(markerapi == null) {
			this.getLogger().severe("Error loading dynmap marker API!");
			return;
		}

		FileConfiguration cfg = getConfig();
		cfg.options().copyDefaults(true);   /* Load defaults, if needed */
		this.saveConfig();  /* Save updates, if needed */

		File shapefile = new File(this.getDataFolder(), this.getConfig().getString("shapefilePath"));

		try {
			Map<String, URL> map = Collections.singletonMap("url", shapefile.toURI().toURL());
			DataStore store = DataStoreFinder.getDataStore(map);
			String typeName = store.getTypeNames()[0];
			featureSource = store.getFeatureSource(typeName);

			while (featureSource.getFeatures().features().hasNext()) {
				SimpleFeature feature = featureSource.getFeatures().features().next();
				// figure out what feature does

				System.out.println(feature.getName());
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

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