package org.oscim.app.search;

import android.util.Log;

import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.utils.FileUtils;
import org.oscim.app.MapLayers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by gustl on 24.03.17.
 */

public class PoiFavoritesHandler {
    HashMap<String, List<PointOfInterest>> poiFavorites;
    private static PoiFavoritesHandler instance;

    private PoiFavoritesHandler() {
        initAllFavorites();
    }

    public static PoiFavoritesHandler getInstance() {
        if (instance == null) instance = new PoiFavoritesHandler();
        return instance;
    }

    public void addFavorite(PointOfInterest poi, File folder) {
        List<PointOfInterest> poiList = poiFavorites.get(folder.getAbsolutePath());
        if (poiList == null) {
            poiList = new ArrayList<PointOfInterest>();
        }
        poiList.add(poi);
        poiFavorites.put(folder.getAbsolutePath(), poiList);
    }

    public void removeFavorite(PointOfInterest poi, File folder) {
        List<PointOfInterest> poiList = poiFavorites.get(folder.getAbsolutePath());
        if (poiList == null) {
            return;
        }
        poiList.remove(poi);
        poiFavorites.put(folder.getAbsolutePath(), poiList);
    }

    public HashMap<String, List<PointOfInterest>> getFavoriteHashMap() {
        return poiFavorites;
    }

    public List<PointOfInterest> getFavorites() {
        List<PointOfInterest> actualPois = new ArrayList<PointOfInterest>();
        Iterator it = poiFavorites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            actualPois.addAll((List<PointOfInterest>) pair.getValue());
        }
        return actualPois;
    }

    private void initAllFavorites() {
        poiFavorites = new HashMap<>();
        List<File> poiFiles = new ArrayList<>();
        for (File folder : MapLayers.MAP_FOLDERS) {
            poiFiles.addAll(FileUtils.walkExtension(folder, ".poi"));
        }
        for (int i = 0; i < poiFiles.size(); i++) {
            File mapDirectory = poiFiles.get(i).getParentFile();
            List<Long> actualPoiIds = fetchFavorites(mapDirectory);
            if (actualPoiIds == null || actualPoiIds.isEmpty()) continue;
            List<PointOfInterest> actualPois = new ArrayList<PointOfInterest>();
            for (long id : actualPoiIds) {
                PointOfInterest poi = getPoiFromIdAndFile(id, poiFiles.get(i));
                if (poi != null)
                    actualPois.add(poi);
            }
            poiFavorites.put(mapDirectory.getAbsolutePath(), actualPois);
        }
    }

    public void storeAllFavorites() {
        Iterator it = poiFavorites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            List<Long> actualPoiIds = new ArrayList<Long>();
            List<PointOfInterest> actualPois = (List<PointOfInterest>) pair.getValue();
            for (PointOfInterest poi : actualPois) {
                actualPoiIds.add(poi.getId());
            }
            storeFavorites(new File((String) pair.getKey()), actualPoiIds);
            //it.remove(); // avoids a ConcurrentModificationException
        }
    }

    public PointOfInterest getPoiFromIdAndFile(long id, File poiFile) {
        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(poiFile);
            return persManager.findPointByID(id);
        } catch (Throwable t) {
            Log.e(t.getMessage(), t.getCause().getMessage());
        } finally {
            if (persManager != null) {
                persManager.close();
            }
        }
        return null;
    }

    public PoiPersistenceManager openPoiConnection(File poiFile) {
        return AndroidPoiPersistenceManagerFactory
                .getPoiPersistenceManager(poiFile.getAbsolutePath());
    }

    private List<Long> fetchFavorites(File folder) {
        File file = new File(folder, "poiFavor.list");
        if (file.exists()) {
            String filePath;
            filePath = file.getAbsolutePath();

            try {
                FileInputStream fileInputStream = new FileInputStream(filePath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

                List<Long> favorMap =
                        (List<Long>) objectInputStream.readObject();
                objectInputStream.close();
                return favorMap;
            } catch (IOException | ClassNotFoundException ex) {
                Log.w("Exception List File", ex.getMessage());
            }
        }
        return null;
    }

    private void storeFavorites(File folder, List<Long> list) {
        File file = new File(folder, "poiFavor.list");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(list);
            objectOutputStream.close();
        } catch (IOException ex) {
            Log.w("Exception List File", ex.getMessage());
        }
    }
}
