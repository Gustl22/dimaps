package org.oscim.app.search;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.MapLayers;
import org.oscim.app.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.oscim.app.location.LocationPersistenceManager.fetchLocations;
import static org.oscim.app.location.LocationPersistenceManager.storeLocations;
import static org.oscim.app.search.PoiSearch.getPoiFromLocationAndFile;

/**
 * Handles storage of favorite POIs
 */

public class PoiFavoritesHandler {
    private LinkedHashMap<String, List<PointOfInterest>> mPoiFavorites;
    private String mName;

    public PoiFavoritesHandler(String name) {
        this.mName = name;
        initAllFavorites();
    }

    /**
     * Add favorite POI to internal list
     *
     * @param poi    POI you want to add
     * @param folder which contains the POI-file
     */
    public void addFavorite(PointOfInterest poi, File folder) {
        List<PointOfInterest> poiList = mPoiFavorites.get(folder.getAbsolutePath());
        if (poiList == null) {
            poiList = new ArrayList<>();
        }
        if (!poiList.contains(poi)) {
            poiList.add(poi);
            mPoiFavorites.put(folder.getAbsolutePath(), poiList);
        }
    }

    /**
     * Remove POI from personal list
     * @param poi POI you want to remove
     * @param folder where POI-list is located.
     */
    public void removeFavorite(PointOfInterest poi, File folder) {
        List<PointOfInterest> poiList = mPoiFavorites.get(folder.getAbsolutePath());
        if (poiList == null) {
            return;
        }
        poiList.remove(poi);
        mPoiFavorites.put(folder.getAbsolutePath(), poiList);
    }

    public LinkedHashMap<String, List<PointOfInterest>> getFavoriteHashMap() {
        return mPoiFavorites;
    }

    public List<PointOfInterest> getFavorites() {
        List<PointOfInterest> actualPois = new ArrayList<>();
        for (Map.Entry<String, List<PointOfInterest>> pair : mPoiFavorites.entrySet()) {
            actualPois.addAll(pair.getValue());
        }
        return actualPois;
    }

    private void initAllFavorites() {
        mPoiFavorites = new LinkedHashMap<>();
        List<File> poiFiles = new ArrayList<>();
        for (File folder : MapLayers.MAP_FOLDERS) {
            poiFiles.addAll(FileUtils.walkExtension(folder, ".poi"));
        }
        for (int i = 0; i < poiFiles.size(); i++) {
            File mapDirectory = poiFiles.get(i).getParentFile();
            List<LatLong> actualPoiIds = fetchLocations(new File(mapDirectory,mName));
            if (actualPoiIds == null || actualPoiIds.isEmpty()) continue;
            List<PointOfInterest> actualPois = new ArrayList<PointOfInterest>();
            for (LatLong latlong : actualPoiIds) {
                PointOfInterest poi = getPoiFromLocationAndFile(latlong, poiFiles.get(i));
                if (poi != null)
                    actualPois.add(poi);
            }
            mPoiFavorites.put(mapDirectory.getAbsolutePath(), actualPois);
        }
    }

    public void storeAllFavorites() {
        for (Map.Entry<String, List<PointOfInterest>> pair : mPoiFavorites.entrySet()) {
            List<LatLong> actualPoiLocations = new ArrayList<>();
            List<PointOfInterest> actualPois = pair.getValue();
            for (PointOfInterest poi : actualPois) {
                actualPoiLocations.add(new LatLong(poi.getLatitude(), poi.getLongitude()));
            }
            storeLocations(new File(pair.getKey(), mName), actualPoiLocations);
            //it.remove(); // avoids a ConcurrentModificationException
        }
    }
}
