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
package org.rebo.app.search;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategory;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.mapsforge.poi.storage.UnknownPoiCategoryException;

import java.io.File;
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
public class PoiSearch {
    private final static int SEARCH_RESULT_LIMIT = 25;
    private final PoiManager mPoiManager;

    // POI categories are based on one POI file, the others should have same categories.
    private Set<String> mCustomPoiCategory = new HashSet<>();
    private static List<PoiPersistenceManager> smPoiPersistenceManagerList = new ArrayList<>();

    public PoiSearch(PoiManager manager) {
        this.mPoiManager = manager;
        initCustomPoiCategories();
    }

    public static PoiPersistenceManager openPoiConnection(File poiFile) {
        PoiPersistenceManager ppm = AndroidPoiPersistenceManagerFactory
                .getPoiPersistenceManager(poiFile.getAbsolutePath());
//        smPoiPersistenceManagerList.add(ppm);
        return ppm;
    }

    public void initCustomPoiCategories() {
        //Add all PoiCategories
        if (mPoiManager.getPoiFile() == null) return;
        PoiPersistenceManager ppm = openPoiConnection(mPoiManager.getPoiFile());
        try {
            Collection<PoiCategory> poiCategories = ppm.getCategoryManager().getRootCategory().deepChildren();
            for (PoiCategory poiCategory : poiCategories) {
                mCustomPoiCategory.add(poiCategory.getTitle());
            }
        } catch (UnknownPoiCategoryException e) {
            e.printStackTrace();
        } finally {
            if (ppm != null)
                ppm.close();
        }
    }

    public static void closePoiPersistenceManagers() {
//        //TODO this takes too long, find another solution for that.
//        for (PoiPersistenceManager poiPersistenceManager : smPoiPersistenceManagerList) {
//               poiPersistenceManager.close();
//        }
//        smPoiPersistenceManagerList.clear();
    }

    public Collection<PointOfInterest> getPoiByAll(String text) {
        Collection<PointOfInterest> collection = new HashSet<>();
        text = text.toLowerCase();
        List<String> requests = new ArrayList<String>(Arrays.asList(text.split("-|\\.|\\s+|,")));
        for (int i = requests.size() - 1; i >= 0; i--) {
            if (requests.get(i).isEmpty()) {
                requests.remove(i);
            }
        }

        if (!requests.isEmpty()) {
            Map<String, String> tagFilter = new HashMap<>();

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
            for (String c : mCustomPoiCategory) {
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
                        StringBuilder builder = new StringBuilder();
                        for (int j = 0; j < requests.size(); j++) {
                            if (i == j) continue;
                            builder.append(requests.get(j)).append(" ");
                        }
                        tagFilter.put("name", builder.toString().trim());
                        Collection<PointOfInterest> res = getPoiByTagsAndCategory(
                                convertTagMap2TagList(tagFilter), c, mPoiManager.getPoiFile());
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
//                    tagFilter.put("name", requests.get(0));
//                    tagFilter.put("addr:city", requests.get(1));
//
//                    collection.addAll(getPoiByTagsAndCategory(convertTagMap2TagList(tagFilter), "Root", mPOI_File));
//
//                    tagFilter.put("addr:city", requests.get(0));
//                    tagFilter.put("name", requests.get(1));
//
//                    collection.addAll(getPoiByTagsAndCategory(convertTagMap2TagList(tagFilter), "Root", mPOI_File));

                    List<Tag> tags = convertTagMap2TagList(tagFilter);
                    tags.add(new Tag("*", requests.get(0)));
                    tags.add(new Tag("*", requests.get(1)));

                    collection.addAll(getPoiByTagsAndCategory(tags, "Root", mPoiManager.getPoiFile()));
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
                        //Searches only for Cities or other places, if only the postcode is mentioned
                        if (tagFilter.containsKey("addr:postcode")) {
                            category = "Places";
                        }
                        tagFilter.remove("name");
                    } else {
                        tagFilter.put("name", builder);
                    }

                    tagFilter.remove("addr:city");

                    collection.addAll(getPoiByTagsAndCategory(
                            convertTagMap2TagList(tagFilter), category, mPoiManager.getPoiFile()));
                    //collection.addAll(getPoiByTagAndCategory("addr:street", text, CustomPoiCategory.Root));
                    //collection.addAll(getPoiByTagAndCategory("highway", "residential", CustomPoiCategory.Root));
                }
            }
        }
        return collection;
    }

    private List<Tag> convertTagMap2TagList(Map tagMap) {
        Iterator<Map.Entry<String, String>> iterator = tagMap.entrySet().iterator();
        List<Tag> tags = new ArrayList<Tag>();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            tags.add(new Tag(entry.getKey(), entry.getValue()));
        }
        return tags;
    }

    public Collection<PointOfInterest> getPoiInBounds(BoundingBox boundingBox, String poiCategory) {
        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(mPoiManager.getPoiFile());
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(poiCategory));
            return persManager.findInRect(boundingBox, categoryFilter, null, SEARCH_RESULT_LIMIT);
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
    public static List<PointOfInterest> getPoisByPoint(LatLong location, int distance, File poiFile) {
        TreeMap<Double, PointOfInterest> map = new TreeMap<>();

        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(poiFile);
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle("Root"));

            Collection<PointOfInterest> pois = persManager.findNearPosition(
                    location, distance,
                    categoryFilter, null, SEARCH_RESULT_LIMIT);

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
     * @param poiFile The POI-file containing the POI
     * @return Collection of POIs
     */
    public static Collection<PointOfInterest> getPoiByTagsAndCategory(List<Tag> tags, String category, File poiFile) {
        PoiPersistenceManager persManager = null;
        assert poiFile != null;
        try {
            persManager = openPoiConnection(poiFile);
            BoundingBox bb = persManager.getPoiFileInfo().bounds;
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(category));
            persManager.getPoiFileInfo();

            if (tags.isEmpty()) {
                return null;
            }
            return persManager.findInRect(bb, categoryFilter,
                    tags, SEARCH_RESULT_LIMIT);
//                    "%"+entry.getKey()+"="+entry.getValue()+"%", Integer.MAX_VALUE);
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
