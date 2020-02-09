package com.ruinscraft.countries;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
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

	private int xOffset = 0;
	private int y = 64;
	private int zOffset = 0;

	private World world;

	private FeatureSource<SimpleFeatureType, SimpleFeature> featureSource;

	@Override
	public void onEnable() {
		/* Get dynmap */
		this.dynmap = getServer().getPluginManager().getPlugin("dynmap");
		if (this.dynmap == null) {
			this.getLogger().severe("Need Dynmap!");
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		this.api = (DynmapAPI) dynmap; /* Get API */

		/* If both enabled, activate */
		if (this.dynmap.isEnabled()) {
			try {
				activate();
			} catch (IOException e) {
				e.printStackTrace();
				this.getPluginLoader().disablePlugin(this);
				return;
			}
		}
	}

	// handles everything
	private void activate() throws IOException {
		this.markerapi = api.getMarkerAPI();
		if (this.markerapi == null) {
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
		this.xOffset = cfg.getInt("xOffset", 0);
		this.y = cfg.getInt("y", 64);
		this.zOffset = cfg.getInt("zOffset", 0);

		File shapefile = new File(this.getDataFolder(), this.getConfig().getString("shapefilePath"));
		if (shapefile == null || !shapefile.isFile()) {
			this.getLogger().warning("Shapefile not found!");
			return;
		}

		FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);

		this.featureSource = store.getFeatureSource();

		// Create new countries markerset
		markerSet = markerapi.getMarkerSet("countries.markerset");
		if(markerSet == null) {
			markerSet = markerapi.createMarkerSet("countries.markerset", cfg.getString("style.layerName", "Countries"), null, false);
		} else {
			markerSet.setMarkerSetLabel(cfg.getString("style.layerName", "Countries"));
		}
		if (markerSet == null) {
			this.getLogger().severe("Error creating marker set");
			return;
		}

		this.world = this.getServer().getWorlds().get(cfg.getInt("world"));
		if (world == null) {
			this.getLogger().severe("No world found!");
			return;
		}

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

			// name of area
			// formerly index 4, which was the semi-governing nation over it (Guam -> USA)
			String countryName = (String) feature.getAttribute(8);

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
						x[i] = (lat * this.scaling) + xOffset;

						y[i] = this.y;

						z[i] = (lon * this.scaling) * -1 + zOffset;
						i++;
					}

					if (markerSet.findPolyLineMarker(id) != null) markerSet.findPolyLineMarker(id).deleteMarker();

					PolyLineMarker polyline = markerSet.createPolyLineMarker(id, 
							countryName, false, world.getName(), x, y, z, false);
					if(polyline == null) {
						this.getLogger().info("error adding area marker " + id);
						continue;
					}

					int color = cfg.getInt("style.color", 0xCC66CC);
					polyline.setLineStyle(cfg.getInt("style.lineThickness", 3), cfg.getDouble("style.lineOpacity", .5), color);

					polygonIndex++;
				}
			}
		}
		iterator.close();

		int minzoom = cfg.getInt("style.minimumZoom", 0);
		if (minzoom > 0) markerSet.setMinZoom(minzoom);
		markerSet.setLayerPriority(cfg.getInt("style.priority", 12));
		markerSet.setHideByDefault(cfg.getBoolean("style.hideByDefault", true));

		if (cfg.getBoolean("style.enableCountryMarkers", true)) handleCountryMarkers();

		this.getLogger().info("Version " + this.getDescription().getVersion() + " is activated!");
	}

	// only run after activate()
	public void handleCountryMarkers() throws IOException {
		Reader reader = this.getTextResource(this.getConfig().getString("countriesFile"));
		if (reader == null) {
			this.getLogger().warning("Countries file not found. Country markers not loaded.");
			return;
		}
		for (String string : CharStreams.readLines(reader)) {
			String[] separated = string.split("\t");

			double x = Double.valueOf(separated[2]) * this.scaling;
			double z = Double.valueOf(separated[1]) * this.scaling * -1;

			markerSet.createMarker(separated[0], separated[3], this.world.getName(), x, this.y, z, 
					markerapi.getMarkerIcon(this.getConfig().getString("style.markerIcon", "king")), false);
		}
	}

}