package com.ruinscraft.countries;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.io.CharStreams;

public class DynmapCountries extends JavaPlugin {

	private Plugin dynmap;
	private DynmapAPI api;
	private MarkerAPI markerapi;
	private MarkerSet markerSet;

	private double scaling;

	private FeatureSource<SimpleFeatureType, SimpleFeature> featureSource;

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
			try {
				activate();
			} catch (IOException e) {
				e.printStackTrace();
				this.getPluginLoader().disablePlugin(this);
				return;
			}
		}
	}

	private void activate() throws IOException {
		markerapi = api.getMarkerAPI();
		if(markerapi == null) {
			this.getLogger().severe("Error loading dynmap marker API!");
			return;
		}

		FileConfiguration cfg = getConfig();
		cfg.options().copyDefaults(true);   /* Load defaults, if needed */
		this.saveConfig();  /* Save updates, if needed */

		if (this.getResource(cfg.getString("countriesFile")) == null) {
			this.saveResource(cfg.getString("countriesFile"), false);
		}
		this.scaling = cfg.getDouble("scaling", 120);

		File shapefile = new File(this.getDataFolder(), this.getConfig().getString("shapefilePath"));
		if (shapefile == null || !shapefile.isFile()) {
			this.getLogger().warning("shapefile not found!!");
			return;
		}

		Map<String, Serializable> map = new HashMap<>();
		try {
			map.put("url", shapefile.toURI().toURL());
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}

		FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);

		this.featureSource = store.getFeatureSource();

		/* Now, add marker set for mobs (make it transient) */
		markerSet = markerapi.getMarkerSet("countries.markerset");
		if(markerSet == null) {
			markerSet = markerapi.createMarkerSet("countries.markerset", cfg.getString("layerName", "Countries"), null, false);
		} else {
			markerSet.setMarkerSetLabel(cfg.getString("layerName", "Countries"));
		}
		if (markerSet == null) {
			this.getLogger().severe("Error creating marker set");
			return;
		}

		World world = this.getServer().getWorlds().get(0);

		FeatureCollection<SimpleFeatureType, SimpleFeature> features;
		try {
			features = featureSource.getFeatures();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}		

		FeatureIterator<SimpleFeature> iterator = features.features();
		int iteration = -1;
		while (iterator.hasNext()) {
			iteration++;
			SimpleFeature feature = iterator.next();

			String countryName = (String) feature.getAttribute(4);

			int index = 0;

			for (Property property : feature.getProperties()) {
				index++;
				String propertyValue = property.getValue().toString();
				if (!propertyValue.contains("(((")) continue;

				String[] polygons = { propertyValue };
				if (propertyValue.contains("), (")) {
					polygons = propertyValue.split(Pattern.quote("), ("));
				}

				int polygonIndex = 0;
				for (String polygon : polygons) {
					String id = countryName + "_" + iteration + "_" + index + "_" + polygonIndex;
					polygon = polygon.replace("MULTIPOLYGON ", "").replace("(", "").replace(")", "");
					String[] locations = polygon.split(", ");

					double[] x = new double[locations.length];
					double[] y = new double[locations.length];
					double[] z = new double[locations.length];

					int i = 0;
					for (String location : locations) {
						String[] coords = location.split(" ");
						double lat = 0;
						double lon = 0;
						try {
							lat = Double.valueOf(coords[0]);
							lon = Double.valueOf(coords[1]);
						} catch (NumberFormatException e) { 
							e.printStackTrace();
							continue;
						}
						x[i] = lat * this.scaling;

						y[i] = 64;

						z[i] = lon * this.scaling * -1;
						i++;
					}

					if (markerSet.findPolyLineMarker(id) != null) markerSet.findPolyLineMarker(id).deleteMarker();

					PolyLineMarker polyline = markerSet.createPolyLineMarker(id, 
							countryName, false, world.getName(), x, y, z, false);
					if(polyline == null) {
						this.getLogger().info("error adding area marker " + id);
						continue;
					}

					int color = 0xCC66CC;
					polyline.setLineStyle(3, .5, color);

					polygonIndex++;
				}
			}
		}
		iterator.close();

		int minzoom = cfg.getInt("layer.minzoom", 0);
		if (minzoom > 0) markerSet.setMinZoom(minzoom);
		markerSet.setLayerPriority(cfg.getInt("layer.layerprio", 12));
		markerSet.setHideByDefault(cfg.getBoolean("layer.hidebydefault", true));

		handleCountryMarkers();

		this.getLogger().info("version " + this.getDescription().getVersion() + " is activated");
	}

	// only run after activate()
	public void handleCountryMarkers() throws IOException {
		Reader reader = this.getTextResource(this.getConfig().getString("countriesFile"));
		for (String string : CharStreams.readLines(reader)) {
			String[] separated = string.split("\t");

			double x = Double.valueOf(separated[2]) * this.scaling;
			double z = Double.valueOf(separated[1]) * this.scaling * -1;

			markerSet.createMarker(separated[0], separated[3], 
					this.getServer().getWorlds().get(0).getName(), x, 64D, z, markerapi.getMarkerIcon("king"), false);
		}
	}

}