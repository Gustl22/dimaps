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
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiFileInfo;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.utils.FileUtils;
import org.oscim.app.MapLayers;
import org.oscim.app.holder.AreaFileInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * POI search.<br/>
 * Long press on map to search inside visible bounding box.<br/>
 * Tap on POIs to show their name (in device's locale).
 */
public class PoiSearch implements PoiSelector {
    private File mPOI_File;
    private PointOfInterest poiArea; //The Area of poiFile expressed as POI

    public PoiSearch() {
    }

    @Override
    public File getPoiFile(int index) {
        return mPOI_File;
    }

    public void setPoiFile(File poiFile) throws FileNotFoundException {
        if (poiFile != null && poiFile.exists()) {
                mPOI_File = poiFile;
                Collection<File> poiAreas = fetchAreaFiles(poiFile.getName());
                if(poiAreas != null && !poiAreas.isEmpty())
                    setPoiArea(getPoiFromFile(new ArrayList<>(poiAreas).get(0), 0));
        } else if (mPOI_File != null && mPOI_File.getParentFile().exists()) {
            setPoiFileByAreaFolder(mPOI_File.getParentFile());
        } else {
            setPoiFileByAreaFolder(null);
        }
    }

    public void setPoiFileByAreaFolder(File areaFolder) throws FileNotFoundException {
        ArrayList<File> files;
        if(areaFolder == null || !areaFolder.exists()){
            files = new ArrayList<File>();
            for (File f : MapLayers.MAP_FOLDERS) {
                files.addAll(FileUtils.walkExtension(f, ".poi"));
            }
        } else {
            files = FileUtils.walkExtension(areaFolder, ".poi");
        }
        if(!files.isEmpty()){
            setPoiFile(files.get(0));
        } else {
            throw new FileNotFoundException("No point of interest files found");
        }
    }

    public PoiPersistenceManager openPoiConnection(File poiFile){
        return AndroidPoiPersistenceManagerFactory
                .getPoiPersistenceManager(poiFile.getAbsolutePath());
    }

    public Collection<PointOfInterest> getPoiByAll(String text){
        Collection<PointOfInterest> collection = new HashSet<PointOfInterest>();
        List<File> files;
        text = text.toLowerCase();
        String[] requests = text.split("-|\\.|\\s+|,");
//        for(int i=0; i<requests.length; i++){
//            requests[i] = requests[i].trim();
//        }
        files = new ArrayList<>(fetchAreaFiles(text));
        for(int i= 0; i< files.size(); i++){
            File f = files.get(i);
            collection.add(getPoiFromFile(f, i));
        }
        if(!collection.isEmpty()) {
            mPOI_File = files.get(files.size()-1);
            return collection;
        } else {

            outer:
            for(CustomPoiCategory c : CustomPoiCategory.values()){
                String cat = c.name().substring(0,c.name().length()-1).toLowerCase();
                for(int i=0; i<requests.length; i++){
                    if(requests[i].contains(cat)){
                        String builder = "";
                        for(int j=0; j<requests.length; j++){
                            if(i==j) continue;
                            builder += requests[j] + " ";
                        }
                        Collection<PointOfInterest> res = getPoiByNameAndCategory(builder.trim(), c);
                        if(res != null && !res.isEmpty()){
                            collection.addAll(res);
                            return collection;
                        }
                    }
                }
            }
            if(collection.isEmpty()){
                collection.addAll(getPoiByNameAndCategory(text, CustomPoiCategory.Root));
            }
        }
        return collection;
    }

    private Collection<File> fetchAreaFiles(String text){
        Collection<File> countryCollection = new HashSet<File>();
        Collection<File> continentCollection = new HashSet<File>();
        Collection<File> regionCollection = new HashSet<File>();
        text = text.toLowerCase();
        ArrayList<File> files = new ArrayList<File>();
        for (File f : MapLayers.MAP_FOLDERS) {
            files.addAll(FileUtils.walkExtension(f, ".poi"));
        }
        if(!files.isEmpty()){
            for(int i= 0; i<files.size(); i++){
                File file = files.get(i);
                AreaFileInfo areaInfo = new AreaFileInfo(file.getAbsolutePath());
                if(text.contains(areaInfo.getRegion().toLowerCase())){
                    regionCollection.add(file);
                    return regionCollection;
                } else if (text.contains(areaInfo.getCountry().toLowerCase())){
                    countryCollection.add(file);
                } else if (text.contains(areaInfo.getContinent().toLowerCase())){
                    continentCollection.add(file);
                }
            }
        }
        if(countryCollection.isEmpty()) return continentCollection;
        return countryCollection;
    }

    public PointOfInterest getPoiFromFile(File file, int poiId){
        AreaFileInfo areaInfo = new AreaFileInfo(file.getAbsolutePath());
        PointOfInterest poi;
        PoiPersistenceManager persManager = openPoiConnection(file);
        PoiFileInfo poiInfo = persManager.getPoiFileInfo();
        BoundingBox bb = poiInfo.bounds;
        LatLong center = bb.getCenterPoint();
        poi = new PointOfInterest(-(poiId+1), center.getLatitude(), center.getLongitude(),
                areaInfo.getContinent()+ ", " + areaInfo.getCountry() + ", "
                        + areaInfo.getRegion(), (new PoiMapareaCategory()));
        persManager.close();
        return poi;
    }

    public Collection<PointOfInterest> getPoiInBounds(BoundingBox boundingBox, CustomPoiCategory poiCategory){
        PoiPersistenceManager persManager = null;
        try {
            persManager = openPoiConnection(mPOI_File);
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(poiCategory.name()));
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

    public Collection<PointOfInterest> getPoiByNameAndCategory(String name, CustomPoiCategory category){
        PoiPersistenceManager persManager = null;
        assert mPOI_File != null;
        try {
            persManager = openPoiConnection(mPOI_File);
            assert persManager != null;
            BoundingBox bb = persManager.getPoiFileInfo().bounds;
            PoiCategoryManager categoryManager = persManager.getCategoryManager();
            PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
            categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle(category.name()));
            persManager.getPoiFileInfo();
            return persManager.findInRect(bb, categoryFilter, "%name="+name+"%", Integer.MAX_VALUE);
        } catch (Throwable t) {
            if(t != null && t.getCause() != null)
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

    //Make shure has a natural name;
    public enum CustomPoiCategory{
        Restaurants,
        Shops,
        Fastfood,
        Bars,
        Electronics,
        Clothes,
        Maparea,
        Root
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
            return getPoiInBounds((BoundingBox) params[0], CustomPoiCategory.Restaurants);
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
