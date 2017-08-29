/*
 * Copyright 2015-2016 devemux86
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.app.search;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategory;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiFileInfo;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.mapsforge.poi.storage.UnknownPoiCategoryException;
import org.openstreetmap.osmosis.osmbinary.file.FileFormatException;
import org.oscim.app.MapLayers;
import org.oscim.app.holder.AreaFileInfo;
import org.oscim.app.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * POI search.<br/>
 * Long press on map to search inside visible bounding box.<br/>
 * Tap on POIs to show their name (in device's locale).
 */
public class PoiSearch implements PoiSelector {
    private File mPOI_File;
    private PointOfInterest poiArea; //The Area of poiFile expressed as POI
    public Set<String> CustomPoiCategory;

    public PoiSearch() {
    }

    @Override
    public File getPoiFile(int index) {
        return mPOI_File;
    }

    public void initPoiFile() throws FileNotFoundException, FileFormatException {
        ArrayList<File> files = getPoiFilesByAreaFolder(null);
        if (files == null || files.isEmpty())
            throw new FileNotFoundException("No point of interest files found");
        setPoiFile(files.get(0));
    }

    /**
     * Sets POI-File for current search, if it not exists,
     * it searches the parent directories for a file
     *
     * @param poiFile The file, which should be set
     * @throws FileNotFoundException If no files exist, it throws an Exception
     */
    public void setPoiFile(File poiFile) throws FileNotFoundException, FileFormatException {
        //if(poiFile == null) return;
        if (poiFile != null && poiFile.exists()) {
            mPOI_File = poiFile;
        } else if (mPOI_File != null && mPOI_File.getParentFile().exists()) {
            ArrayList<File> files = getPoiFilesByAreaFolder(mPOI_File.getParentFile());
            if (files == null || files.isEmpty())
                throw new FileNotFoundException("No point of interest files found");
            mPOI_File = files.get(0);
        } else {
            throw new FileNotFoundException("Point of Interest not exists");
        }
        if (mPOI_File != null) {
            Collection<File> poiAreas = fetchAreaFiles(mPOI_File.getName());
            //Sets the current poiFile as a PointOfInterest
            if (poiAreas != null && !poiAreas.isEmpty())
                setPoiArea(getPoiFromFile(new ArrayList<>(poiAreas).get(0), 0));
        } else {
            throw new FileNotFoundException("No point of interest files found");
        }


        //Add all PoiCategories
        CustomPoiCategory = new HashSet<String>(Arrays.asList("Maparea", "Root"));
        PoiPersistenceManager ppm = openPoiConnection(mPOI_File);
        try {
            Collection<PoiCategory> poiCategories = ppm.getCategoryManager().getRootCategory().deepChildren();
            for (PoiCategory poiCategory : poiCategories) {
                CustomPoiCategory.add(poiCategory.getTitle());
            }
        } catch (UnknownPoiCategoryException e) {
            e.printStackTrace();
        } finally {
            if (ppm != null)
                ppm.close();
        }
    }

    public ArrayList<File> getPoiFilesByAreaFolder(File areaFolder) {
        ArrayList<File> files;
        if (areaFolder == null || !areaFolder.exists()) {
            files = new ArrayList<File>();
            if (MapLayers.MAP_FOLDERS == null) return null;
            for (File f : MapLayers.MAP_FOLDERS) {
                files.addAll(FileUtils.walkExtension(f, ".poi"));
            }
        } else {
            files = FileUtils.walkExtension(areaFolder, ".poi");
        }
        return files;
    }

    public static PoiPersistenceManager openPoiConnection(File poiFile) {
        return AndroidPoiPersistenceManagerFactory
                .getPoiPersistenceManager(poiFile.getAbsolutePath());
    }

    public Collection<PointOfInterest> getPoiByAll(String text) throws FileFormatException {
        Collection<PointOfInterest> collection = new HashSet<PointOfInterest>();
        List<File> files;
        text = text.toLowerCase();
        List<String> requests = new ArrayList<String>(Arrays.asList(text.split("-|\\.|\\s+|,")));
//        for(int i=0; i<requests.length; i++){
//            requests[i] = requests[i].trim();
//        }
        //Search for Poi Files
        files = new ArrayList<>(fetchAreaFiles(text));
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            collection.add(getPoiFromFile(f, i));
        }
        if (!collection.isEmpty()) {
            mPOI_File = files.get(files.size() - 1);
            return collection;
        } else {
            Map<String, String> tagFilter = new HashMap();

            //Postcodes and house numbers
            String houseNo = "";
            String postCode = "";
            for (String request : requests) {
                try {
                    int no = Integer.parseInt(request);
                    if (no > 1000) {
                        postCode = request;
                    } else {
                        houseNo = request;
                    }
                } catch (NumberFormatException ex) {
                    continue;
                }
            }

            if (!houseNo.isEmpty()) {
                tagFilter.put("addr:housenumber", houseNo);
                requests.remove(houseNo);
            }
            if (!postCode.isEmpty()) {
                tagFilter.put("addr:postcode", postCode);
                requests.remove(postCode);
            }

            //Poi category filter
            for (String c : CustomPoiCategory) {
                String cat;
                //Category name without "s"
                if (c.endsWith("s")) {
                    if (c.endsWith("ies")) {
                        cat = c.substring(0, c.length() - 3).toLowerCase();
                    } else {
                        cat = c.substring(0, c.length() - 1).toLowerCase();
                    }
                } else {
                    cat = c.toLowerCase();
                }
                for (int i = 0; i < requests.size(); i++) {
                    if (requests.get(i).contains(cat)) {
                        String builder = "";
                        for (int j = 0; j < requests.size(); j++) {
                            if (i == j) continue;
                            builder += requests.get(j) + " ";
                        }
                        tagFilter.put("name", builder.trim());
                        Collection<PointOfInterest> res = getPoiByTagsAndCategory(tagFilter, c, mPOI_File);
                        if (res != null && !res.isEmpty()) {
                            collection.addAll(res);
                            if (collection.isEmpty()) continue;
                            return collection;
                        }
                    }
                }
            }
            if (collection.isEmpty()) {
                if (requests.size() > 1) {
                    tagFilter.put("name", requests.get(0));
                    tagFilter.put("addr:city", requests.get(1));

                    collection.addAll(getPoiByTagsAndCategory(tagFilter, "Root", mPOI_File));

                    tagFilter.put("addr:city", requests.get(0));
                    tagFilter.put("name", requests.get(1));

                    collection.addAll(getPoiByTagsAndCategory(tagFilter, "Root", mPOI_File));
                }
                //collection.addAll(getPoiByTagAndCategory("addr:street", text, CustomPoiCategory.Root));
                //collection.addAll(getPoiByTagAndCategory("highway", "residential", CustomPoiCategory.Root));
                if (collection.isEmpty()) {
                    String category = "Root";
                    String builder = "";
                    for (String request : requests) {
                        builder += (" " + request);
                    }
                    builder = builder.trim();
                    //Extras
                    if (builder.isEmpty()) {
                        if (tagFilter.containsKey("addr:postcode")) {
                            category = "Places";
                        }
                    }

                    tagFilter.put("name", builder);
                    tagFilter.remove("addr:city");

                    collection.addAll(getPoiByTagsAndCategory(tagFilter, category, mPOI_File));
                    //collection.addAll(getPoiByTagAndCategory("addr:street", text, CustomPoiCategory.Root));
                    //collection.addAll(getPoiByTagAndCategory("highway", "residential", CustomPoiCategory.Root));
                }
            }
        }
        return collection;
    }

    private Collection<File> fetchAreaFiles(String text) {
        Collection<File> countryCollection = new HashSet<File>();
        Collection<File> continentCollection = new HashSet<File>();
        Collection<File> regionCollection = new HashSet<File>();
        text = text.toLowerCase();
        ArrayList<File> files = new ArrayList<File>();
        for (File f : MapLayers.MAP_FOLDERS) {
            files.addAll(FileUtils.walkExtension(f, ".poi"));
        }
        if (!files.isEmpty()) {
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                AreaFileInfo areaInfo = new AreaFileInfo(file.getAbsolutePath());
                if (text.contains(areaInfo.getRegion().toLowerCase())) {
                    regionCollection.add(file);
                    return regionCollection;
                } else if (text.contains(areaInfo.getCountry().toLowerCase())) {
                    countryCollection.add(file);
                } else if (text.contains(areaInfo.getContinent().toLowerCase())) {
                    continentCollection.add(file);
                }
            }
        }
        if (countryCollection.isEmpty()) return continentCollection;
        return countryCollection;
    }

    /**
     * Gets the country poi of a Poi-File
     *
     * @param file
     * @param poiId
     * @return
     */
    public PointOfInterest getPoiFromFile(File file, int poiId) throws FileFormatException {
        AreaFileInfo areaInfo = new AreaFileInfo(file.getAbsolutePath());
        PointOfInterest poi;
        try {
            PoiPersistenceManager persManager = openPoiConnection(file);
            PoiFileInfo poiInfo = persManager.getPoiFileInfo();
            BoundingBox bb = poiInfo.bounds;
            LatLong center = bb.getCenterPoint();
            poi = new PointOfInterest(-(poiId + 1), center.getLatitude(), center.getLongitude(),
                    areaInfo.getContinent() + ", " + areaInfo.getCountry() + ", "
                            + areaInfo.getRegion(), (new PoiMapareaCategory()));
            persManager.close();
        } catch (Exception ex) {
            throw new FileFormatException("Wrong file format. Check poi-file version");
        }
        return poi;
    }

    public Collection<PointOfInterest> getPoiInBounds(BoundingBox boundingBox, String poiCategory) {
        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(mPOI_File);
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(poiCategory));
            return persManager.findInRect(boundingBox, categoryFilter, null, Integer.MAX_VALUE);
        } catch (Throwable t) {
            Log.e(t.getMessage(), t.getCause().getMessage());
        } finally {
            if (persManager != null) {
                persManager.close();
            }
        }
        return null;
    }

    /**
     * @param location point
     * @param distance in meters
     * @return List of POIs sorted by it's distance
     */
    public static List<PointOfInterest> getPoisByPoint(LatLong location, int distance, File mPOI_File) {
        TreeMap<Double, PointOfInterest> map = new TreeMap<>();

        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(mPOI_File);
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle("Root"));

            Collection<PointOfInterest> pois = persManager.findNearPosition(
                    location, distance,
                    categoryFilter, null, Integer.MAX_VALUE);

            for (PointOfInterest poi : pois) {
                LatLong cur = new LatLong(poi.getLatitude(), poi.getLongitude());
                //I think no point would exactly have the same distance as another
                map.put(cur.distance(location), poi);
            }
            return new ArrayList<>(map.values());
        } catch (Throwable t) {
            Log.e(t.getMessage(), t.getCause().getMessage());
        } finally {
            if (persManager != null) {
                persManager.close();
            }
        }
        return null;
    }

    /**
     * Get POIs by Tags and a category
     * @param tags The tag map you search for
     * @param category The category you search in
     * @param mPOI_File The POI-file containing the POI
     * @return Collection of POIs
     */
    public static Collection<PointOfInterest> getPoiByTagsAndCategory(Map<String, String> tags, String category, File mPOI_File) {
        PoiPersistenceManager persManager = null;
        assert mPOI_File != null;
        try {
            persManager = openPoiConnection(mPOI_File);
            assert persManager != null;
            BoundingBox bb = persManager.getPoiFileInfo().bounds;
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(category));
            persManager.getPoiFileInfo();

            Map<String, String> query = new HashMap<>();

            Iterator it = tags.entrySet().iterator();
            if (!it.hasNext()) {
                return null;
            }
            while (it.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();
                if ((pair.getValue()).isEmpty()) continue;
                query.put(pair.getKey(), pair.getValue() + "%");
            }
            return persManager.findInRect(bb, categoryFilter,
                    query, Integer.MAX_VALUE);
        } catch (Throwable t) {
            if (t.getCause() != null)
                Log.e(t.getMessage(), t.getCause().getMessage());
        } finally {
            if (persManager != null) {
                persManager.close();
            }
        }
        return null;
    }

    /**
     * Gets a PointOfInterest based on the location
     *
     * @param location Location u want its POI
     * @param poiFile  The file containing the location
     * @return Nearest PointOfInterest
     */
    public static PointOfInterest getPoiFromLocationAndFile(LatLong location, File poiFile) {
        List<PointOfInterest> poisByPoint = getPoisByPoint(location, 1, poiFile);
        if (poisByPoint != null && !poisByPoint.isEmpty()) {
            return poisByPoint.get(0);
        }
        return null;
    }

    public static PointOfInterest getPoiFromIdAndFile(long id, File poiFile) {
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

    public void setPoiArea(PointOfInterest poiArea) {
        this.poiArea = poiArea;
    }

    public PointOfInterest getPoiArea() {
        return poiArea;
    }


    /**
     * @params .execute(BoundingBox bounds, File poiFile)
     */
    private class PoiSearchTask extends AsyncTask<Object, Void, Collection<PointOfInterest>> {
        private final String category;
        private Activity activity;

        private PoiSearchTask(Activity activity, String category) {
            this.activity = activity;
            this.category = category;
        }

        @Override
        protected Collection<PointOfInterest> doInBackground(Object... params) {
            return getPoiInBounds((BoundingBox) params[0], "Restaurants");
        }

        @Override
        protected void onPostExecute(Collection<PointOfInterest> pointOfInterests) {
//            if (activity == null) {
//                return;
//            }
//            Toast.makeText(activity, category + ": " + (pointOfInterests != null ? pointOfInterests.size() : 0), Toast.LENGTH_SHORT).show();
//            if (pointOfInterests == null) {
//                return;
//            }
//
//            GroupLayer groupLayer = new GroupLayer(App.map);
//            for (final PointOfInterest pointOfInterest : pointOfInterests) {
//                final Circle circle = new Circle(pointOfInterest.getLatLong(), 16, 3, null) {
//                    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY, GeoPoint p) {
//                        // GroupLayer does not have a position, layerXY is null
//                        Point circleXY = App.view..getMapViewProjection().toPixels(getPosition());
//                        if (this.contains(circleXY, tapXY)) {
//                            Toast.makeText(activity, pointOfInterest.getName(), Toast.LENGTH_SHORT).show();
//                            return true;
//                        }
//                        return false;
//                    }
//                };
//                groupLayer.layers.add(circle);
//            }
//            App.map.layers().add(groupLayer);
//            App.map.updateMap(true);
        }
    }
}
