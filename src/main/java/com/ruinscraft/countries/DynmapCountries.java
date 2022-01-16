package com.ruinscraft.countries;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DynmapCountries extends JavaPlugin {

	private DynmapAPI api;
	private MarkerAPI markerapi;
	private MarkerSet markerSet;

	private double scaling;

	private int y = 64;

	FileConfiguration cfg;

	@Override
	public void onEnable() {
		/* Get dynmap */
		Plugin dynmap = getServer().getPluginManager().getPlugin("dynmap");

		if (!(dynmap instanceof DynmapAPI)) {
			this.getLogger().warning("Dynmap not found");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		this.api = (DynmapAPI) dynmap; /* Get API */

		/* If both enabled, activate */
		if (dynmap.isEnabled()) {
			try {
				activate();
			} catch (IOException e) {
				e.printStackTrace();
				getServer().getPluginManager().disablePlugin(this);
			}
		}
	}

	/**
	 * Used to load config.yml and shapefile resources
	 * @param resource file path
	 * @return File
	 */
	public File loadResource(String resource) {
        File folder = getDataFolder();
        if (!folder.exists()) {
			if (!folder.mkdir()) {
				this.getLogger().warning("Resource " + resource + " could not be loaded");
				return null;
			}
		}
        File resourceFile = new File(folder, resource);
        try {
            if (!resourceFile.exists()) {
				if (!resourceFile.createNewFile()) {
					this.getLogger().warning("Resource " + resource + " could not be created");
					return null;
				}
                try (InputStream in = this.getResource(resource);
                     OutputStream out = new FileOutputStream(resourceFile)) {
                	if (in == null || out == null) {
						this.getLogger().warning("Resource " + resource + " could not be located");
						return resourceFile;
					}
                    ByteStreams.copy(in, out);
                }
                // I don't think this section of code is ever reached. But doesn't hurt to leave it in
                if (!resourceFile.isFile() || resourceFile.length() == 0) {
                	resourceFile.delete();
					this.getLogger().warning("Resource " + resource + " was not found");
					return resourceFile;
				}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resourceFile;
    }

	/**
	 * Handles everything
	 * @throws IOException if something happens
	 */
	private void activate() throws IOException {
		this.markerapi = api.getMarkerAPI();
		if (this.markerapi == null) {
			this.getLogger().severe("Error loading dynmap marker API!");
			return;
		}

		File configFile = this.loadResource("config.yml");
		if (configFile == null) {
			return;
		}
		this.cfg = YamlConfiguration.loadConfiguration(configFile);

		// Create new countries markerset
		markerSet = markerapi.getMarkerSet("countries.markerset");
		if(markerSet == null) {
			markerSet = markerapi.createMarkerSet("countries.markerset",
					cfg.getString("layerName", "Countries"), null, false);
		} else {
			markerSet.setMarkerSetLabel(cfg.getString("layerName", "Countries"));
		}
		if (markerSet == null) {
			this.getLogger().severe("Error creating marker set");
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		int minzoom = cfg.getInt("minimumZoom", 0);
		if (minzoom > 0) markerSet.setMinZoom(minzoom);
		markerSet.setLayerPriority(cfg.getInt("priority", 12));
		markerSet.setHideByDefault(cfg.getBoolean("hideByDefault", true));

		for (String section : cfg.getConfigurationSection("shapefiles").getKeys(false)) {
			section = "shapefiles." + section;
			this.scaling = 120000 / cfg.getDouble(section + "." + "scaling", 1000);
			int xOffset = cfg.getInt(section + "." + "xOffset", 0);
			this.y = cfg.getInt(section + "." + "y", 64);
			int zOffset = cfg.getInt(section + "." + "zOffset", 0);

			boolean errors = false;

			String fileName = this.getConfig().getString(section + "." + "shapefilePath", "countryborders");
			File shapefile = new File(this.getDataFolder(), fileName + ".shp");
			if (shapefile == null || !shapefile.isFile()) {
				shapefile = this.loadResource(fileName + ".shp");
				if (!shapefile.isFile() || shapefile.length() == 0) {
					this.getLogger().warning("Shapefile " + fileName + " not found!");
					shapefile.delete();
					continue;
				} else {
					File shx = this.loadResource(fileName + ".shx");
					File prj = this.loadResource(fileName + ".prj");
					File dbf = this.loadResource(fileName + ".dbf");
					List<File> additionalFiles = List.of(shx, prj, dbf);

					boolean exit = false;
					for (File file : additionalFiles) {
						if (!file.isFile() || file.length() == 0) {
							this.getLogger().warning(file.getName() + " not found! ");
							file.delete();
							exit = true;
							continue;
						}
					}
					if (exit) {
						this.getLogger().warning("One or more additional files could not be located for shapefile " + fileName + ".shp");
						this.getLogger().warning("Shapefile" + fileName + ".shp not loaded!");
						continue;
					}
				}
			}

			FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);

			SimpleFeatureSource featureSource = store.getFeatureSource();

			World world = this.getServer().getWorld(cfg.getString(section + "." + "world"));
			if (world == null) {
				this.getLogger().severe("No world found!");
				store.dispose();
				this.getPluginLoader().disablePlugin(this);
				return;
			}

			if (featureSource.getSchema() == null || featureSource.getSchema().getCoordinateReferenceSystem() == null) {
				this.getLogger().warning("Could not load .prj file for Shapefile " + fileName + ".");
				store.dispose();
				continue;
			}
			CoordinateReferenceSystem data = featureSource.getSchema().getCoordinateReferenceSystem();
			String code = data.getName().getCode();
			System.out.println(code);
			if (!code.contains("WGS_1984") && !code.contains("wgs_1984")) {
				this.getLogger().info("Translating " + fileName + " to a readable format...");
				try {
					this.translateCRS(featureSource, shapefile);
					this.getLogger().info("Translating finished.");
				} catch (FactoryException e) {
					e.printStackTrace();
					store.dispose();
					continue;
				}
			}

			FeatureCollection<SimpleFeatureType, SimpleFeature> features;
			try {
				features = featureSource.getFeatures();
			} catch (IOException e) {
				e.printStackTrace();
				store.dispose();
				this.getPluginLoader().disablePlugin(this);
				return;
			}

			int iteration = -1;
			try (FeatureIterator<SimpleFeature> iterator = features.features()) {
				while (iterator.hasNext()) {
					iteration++;
					SimpleFeature feature = iterator.next();

					int index = 0;

					for (Property property : feature.getProperties()) {
						index++;
						if (property.getValue() == null || property.getValue().toString() == null) {
							errors = true;
							continue;
						}
						String propertyValue = property.getValue().toString();
						if (!propertyValue.contains("((")) continue;

						String[] polygons = { propertyValue };
						if (propertyValue.contains("), (")) {
							polygons = propertyValue.split(Pattern.quote("), ("));
						}

						int polygonIndex = 0;
						for (String polygon : polygons) {
							String id = section +  "_" + iteration + "_" + index + "_" + polygonIndex;
							polygon = polygon.replace("MULTIPOLYGON ", "").replace("(", "").replace(")", "");
							String[] locations = polygon.split(", ");

							double[] x = new double[locations.length];
							double[] y = new double[locations.length];
							double[] z = new double[locations.length];

							int i = 0;
							boolean problemWithNumbers = false;
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
								if (lat + 180 > 360 || lon + 180 > 360) {
									errors = true;
									problemWithNumbers = true;
									break;
								}
								x[i] = (lat * this.scaling) + xOffset;

								y[i] = this.y;

								z[i] = (lon * this.scaling) * -1 + zOffset;
								i++;
							}
							if (problemWithNumbers) continue;

							if (markerSet.findPolyLineMarker(id) != null) markerSet.findPolyLineMarker(id).deleteMarker();

							PolyLineMarker polyline = markerSet.createPolyLineMarker(id,
									"", false, world.getName(), x, y, z, false);
							if(polyline == null) {
								this.getLogger().info("Error adding polyline " + id);
								continue;
							}

							int color = cfg.getInt(section + "." + "style.color", 0xCC66CC);
							polyline.setLineStyle(cfg.getInt(section + "." + "style.lineThickness", 3),
									cfg.getDouble(section + "." + "style.lineOpacity", .5), color);

							polygonIndex++;
						}
					}
				}
			} catch (Exception e) { // can happen from a bad cast or something
				store.dispose();
				e.printStackTrace();
				this.getPluginLoader().disablePlugin(this);
				return;
			} finally {
				store.dispose();
			}
			if (errors) {
				this.getLogger().warning("Shapefile " + fileName + " had errors on load and may be partially" +
						" or completely unloaded. Shapefile is likely incorrectly formatted");
			} else {
				this.getLogger().info("Shapefile " + fileName + " successfully loaded!");
			}
		}

		if (cfg.getBoolean("enableCountryMarkers", true)) handleCountryMarkers();
		this.getLogger().info("Version " + this.getDescription().getVersion() + " is activated!");
	}

	/**
	 * Handles country markers
	 * Only run after activate()
	 * @throws IOException
	 */
	public void handleCountryMarkers() throws IOException {
		Reader reader = this.getTextResource("countries.txt");
		if (reader == null) {
			this.getLogger().warning("Countries file not found. Country markers not loaded.");
			return;
		}

		String worldName = this.getConfig().getString("countryMarkersWorld", "world");
		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			this.getLogger().warning("World name for country markers is null! Country markers not loaded.");
			return;
		}

		for (String string : CharStreams.readLines(reader)) {
			String[] separated = string.split("\t");

			double x = Double.valueOf(separated[2]) * this.scaling;
			double z = Double.valueOf(separated[1]) * this.scaling * -1;

			markerSet.createMarker(separated[0], separated[3], world.getName(), x, this.y, z,
					markerapi.getMarkerIcon(this.getConfig().getString("markerIcon", "king")), false);
		}
		this.getLogger().info("Country markers enabled!");
	}

	public void translateCRS(SimpleFeatureSource featureSource, File shapefile) throws FactoryException, IOException {
		SimpleFeatureType schema = featureSource.getSchema();

		CoordinateReferenceSystem otherCRS = schema.getCoordinateReferenceSystem();
		CoordinateReferenceSystem worldCRS = DefaultGeographicCRS.WGS84;

		MathTransform transform = CRS.findMathTransform(otherCRS, worldCRS, true);
		SimpleFeatureCollection featureCollection = featureSource.getFeatures();

		// how do i file
		System.out.println(shapefile.getParent() + "   " + shapefile.getName());
		File newFile = new File(shapefile.getParent(), shapefile.getName() + "copying.shp");
		newFile.createNewFile();

		DataStoreFactorySpi factory = new ShapefileDataStoreFactory();
		Map<String, Serializable> create = new HashMap<String, Serializable>();
		create.put("url", newFile.toURI().toURL());
		create.put("create spatial index", Boolean.TRUE);
		DataStore dataStore = factory.createNewDataStore(create);
		SimpleFeatureType featureType = SimpleFeatureTypeBuilder.retype(schema, worldCRS);
		dataStore.createSchema(featureType);

		Transaction transaction = new DefaultTransaction("Reproject");
		FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
				dataStore.getFeatureWriterAppend(featureType.getTypeName(), transaction);
		SimpleFeatureIterator iterator = featureCollection.features();
		try {
			while (iterator.hasNext()) {
				// copy the contents of each feature and transform the geometry
				SimpleFeature feature = iterator.next();
				SimpleFeature copy = writer.next();
				copy.setAttributes(feature.getAttributes());

				Geometry geometry = (Geometry) feature.getDefaultGeometry();
				Geometry geometry2 = JTS.transform(geometry, transform);

				copy.setDefaultGeometry(geometry2);
				writer.write();
			}
			transaction.commit();
		} catch (Exception e) {
			e.printStackTrace();
			transaction.rollback();
		} finally {
			writer.close();
			iterator.close();
			transaction.close();
		}
	}

}