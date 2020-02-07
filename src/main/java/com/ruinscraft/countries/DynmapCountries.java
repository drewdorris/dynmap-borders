package com.ruinscraft.countries;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

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

		try {
			FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);

			this.featureSource = store.getFeatureSource();

			long features = featureSource.getFeatures().size();
			System.out.println("features: " + features);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

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

			if (countryName.contains("South Africa")) {
				for (Property property : feature.getProperties()) {
					System.out.print(property.getValue().toString() + " // ");
				}
				System.out.println();
			}

			int index = 0;

			double keepTrackOfLats = 0;
			double keepTrackOfLons = 0;

			int total = 0;

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
						keepTrackOfLats += lat * this.scaling;
						x[i] = lat * this.scaling;

						y[i] = 64;

						keepTrackOfLons += lon * this.scaling * -1;
						z[i] = lon * this.scaling * -1;
						i++;
						total++;
					}

					if (markerSet.findPolyLineMarker(id) != null) markerSet.findPolyLineMarker(id).deleteMarker();

					PolyLineMarker polyline = markerSet.createPolyLineMarker(id, 
							countryName, false, world.getName(), x, y, z, false);
					if(polyline == null) {
						this.getLogger().info("error adding area marker " + id);
						continue;
					}
					polyline.setLineStyle(3, .5, 0xFF00FF);
					polygonIndex++;
				}
			}
			keepTrackOfLats = keepTrackOfLats / total;
			keepTrackOfLons = keepTrackOfLons / total;
			markerSet.createMarker(countryName + "_" + iteration + "_" + index, countryName, Bukkit.getWorlds().get(0).getName(), 
					keepTrackOfLats, 64D, keepTrackOfLons, markerapi.getMarkerIcon("king"), false);
		}
		iterator.close();

		int minzoom = cfg.getInt("layer.minzoom", 0);
		if (minzoom > 0) markerSet.setMinZoom(minzoom);
		markerSet.setLayerPriority(cfg.getInt("layer.layerprio", 12));
		markerSet.setHideByDefault(cfg.getBoolean("layer.hidebydefault", true));

		this.getLogger().info("version " + this.getDescription().getVersion() + " is activated");
	}

}