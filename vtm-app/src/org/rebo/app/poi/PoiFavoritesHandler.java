package org.rebo.app.poi;

import android.util.SparseArray;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.poi.storage.PointOfInterest;
import org.rebo.app.App;
import org.rebo.app.location.LocationPersistenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.rebo.app.poi.PoiSearch.getPoiFromLocationAndFile;

/**
 * Handles storage of favorite POIs
 */

public class PoiFavoritesHandler {
    public static final String POI_FAVOR_FILE = "poiFavor.list";

    private final LinkedHashMap<Integer, List<PointOfInterest>> mPoiFavorites = new LinkedHashMap<>();
    private String mName;
    private final PoiManager mPoiManager;

    public PoiFavoritesHandler(PoiManager manager) {
        this(POI_FAVOR_FILE, manager);
    }

    public PoiFavoritesHandler(String name, PoiManager manager) {
        this.mName = name;
        this.mPoiManager = manager;
        initAllFavorites();
    }

    /**
     * Add favorite POI to internal list
     *
     * @param poi    POI you want to add
     * @param poiFileId id of poi file
     */
    public void addFavorite(PointOfInterest poi, int poiFileId) {
        List<PointOfInterest> poiList = mPoiFavorites.get(poiFileId);
        if (poiList == null) {
            poiList = new ArrayList<>();
        }
        if (!poiList.contains(poi)) {
            poiList.add(poi);
            mPoiFavorites.put(poiFileId, poiList);
        }
    }

    /**
     * Remove POI from personal list
     *
     * @param poi    POI you want to remove
     * @param poiFileId id of poi file
     */
    public void removeFavorite(PointOfInterest poi, int poiFileId) {
        List<PointOfInterest> poiList = mPoiFavorites.get(poiFileId);
        if (poiList == null) {
            return;
        }
        poiList.remove(poi);
        mPoiFavorites.put(poiFileId, poiList);
    }

    public LinkedHashMap<Integer, List<PointOfInterest>> getFavoriteHashMap() {
        return mPoiFavorites;
    }

    public List<PointOfInterest> getFavorites() {
        List<PointOfInterest> actualPois = new ArrayList<>();
        for (Map.Entry<Integer, List<PointOfInterest>> pair : mPoiFavorites.entrySet()) {
            actualPois.addAll(pair.getValue());
        }
        return actualPois;
    }

    private void initAllFavorites() {
        mPoiFavorites.clear();
        SparseArray<File> poiFiles = mPoiManager.getPoiFiles();
        // Iterate through all files and find POI of stored locations, if existent.
        for (int i = 0; i < poiFiles.size(); i++) {
            File mapDirectory = poiFiles.valueAt(i).getParentFile();
            File favorListFile = new File(mapDirectory, mName);
            if (favorListFile.exists()) {
                try {
                    List<LatLong> poiLocations = LocationPersistenceManager.fetchLocations(favorListFile);
                    if (poiLocations == null || poiLocations.isEmpty()) continue;
                    List<PointOfInterest> pois = new ArrayList<>();
                    for (LatLong latlong : poiLocations) {
                        PointOfInterest poi = getPoiFromLocationAndFile(latlong, poiFiles.valueAt(i));
                        if (poi != null)
                            pois.add(poi);
                    }
                    mPoiFavorites.put(poiFiles.keyAt(i), pois);
                } catch (ClassCastException ex) {
                    favorListFile.delete();
                    App.activity.showToastOnUiThread("Poi-favorites deleted, because they have wrong format.");
                }
            }
        }
    }

    public void storeAllFavorites() {
        for (Map.Entry<Integer, List<PointOfInterest>> pair : mPoiFavorites.entrySet()) {
            List<LatLong> actualPoiLocations = new ArrayList<>();
            List<PointOfInterest> actualPois = pair.getValue();
            for (PointOfInterest poi : actualPois) {
                actualPoiLocations.add(new LatLong(poi.getLatitude(), poi.getLongitude()));
            }
            LocationPersistenceManager.storeLocations(
                    new File(mPoiManager.getPoiFiles().get(pair.getKey()).getParent(), mName),
                    actualPoiLocations);
        }
    }
}
