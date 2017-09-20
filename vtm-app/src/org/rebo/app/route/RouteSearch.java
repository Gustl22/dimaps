/*
 * Copyright 2012 osmdroid: M.Kergall
 * Copyright 2012 Hannes Janetzek
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
package org.rebo.app.route;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.Path;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.oscim.android.canvas.AndroidGraphics;
import org.rebo.app.App;
import org.rebo.app.MapLayers;
import org.rebo.app.R;
import org.rebo.app.graphhopper.CrossMapCalculator;
import org.rebo.app.graphhopper.GHPointArea;
import org.rebo.app.graphhopper.GHPointAreaRoute;
import org.rebo.app.graphhopper.GHPointListener;
import org.rebo.app.graphhopper.GraphhopperOsmdroidAdapter;
import org.rebo.app.location.LocationPersistenceManager;
import org.rebo.app.navigation.Navigation;
import org.rebo.app.preferences.StoragePreference;
import org.rebo.app.utils.FileUtils;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.PathLayer;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.theme.styles.LineStyle;
import org.osmdroid.overlays.DefaultInfoWindow;
import org.osmdroid.overlays.ExtendedMarkerItem;
import org.osmdroid.overlays.ItemizedOverlayWithBubble;
import org.osmdroid.routing.Route;
import org.rebo.app.graphhopper.OsmdroidGraphhopperAdapter;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static org.rebo.app.App.activity;
import static org.rebo.app.graphhopper.GraphhopperOsmdroidAdapter.convertGHPointToGeoPoint;
import static org.rebo.app.graphhopper.GraphhopperOsmdroidAdapter.convertPointListToGeoPoints;

public class RouteSearch implements GHPointListener {
    public static int START_INDEX = -2, DEST_INDEX = -1;
    //other points which aren't contained in route are negative

    private final PathLayer mRouteOverlay;
    //private final ItemizedOverlayWithBubble<ExtendedOverlayItem> mRouteMarkers;
    private final ItemizedOverlayWithBubble<ExtendedMarkerItem> mItineraryMarkers;

    private final RouteBar mRouteBar;

    private GHPointArea mStartPoint, mDestinationPoint;
    private final ArrayList<GHPointArea> mViaPoints;
    private ArrayList<GHPointArea> mNonRoutePoints;
    private ArrayList<Integer> mNonRoutePointColors;

    private ExtendedMarkerItem markerStart, markerDestination;

    private UpdateRouteTask mRouteTask;
    private PointList mActualRoute = null;
    private Navigation mNavigation;

    private static ArrayList<File> ghFiles;

    //private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;

    public RouteSearch() {
        mViaPoints = new ArrayList<GHPointArea>();
        mNonRoutePoints = new ArrayList<GHPointArea>();
        mNonRoutePointColors = new ArrayList<>();

        // Itinerary markers:
        ArrayList<ExtendedMarkerItem> waypointsItems = new ArrayList<ExtendedMarkerItem>();

        mItineraryMarkers = new ItemizedOverlayWithBubble<ExtendedMarkerItem>(App.map,
                activity,
                null,
                waypointsItems,
                new ViaPointInfoWindow(R.layout.itinerary_bubble));

        //updateIternaryMarkers();

        //Route and Directions
        //ArrayList<ExtendedOverlayItem> routeItems = new ArrayList<ExtendedOverlayItem>();
        //mRouteMarkers = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(App.map, App.activity,
        //        null, routeItems);
        LineStyle routeLine = new LineStyle(Color.parseColor("#CC3377FF"), 20, Paint.Cap.ROUND);
        mRouteOverlay = new PathLayer(App.map, routeLine);

        // TODO use LayerGroup
        App.map.layers().add(mRouteOverlay);
        //App.map.getOverlays().add(mRouteMarkers);
        App.map.layers().add(mItineraryMarkers);

        mRouteBar = new RouteBar(activity);

        ghFiles = new ArrayList<File>();
        for (File f : MapLayers.MAP_FOLDERS) {
            ghFiles.addAll(FileUtils.walkExtension(f, "-gh"));
            ghFiles.add(f); //Add default folder for loading unzipped GH-Files
        }

        //fetch stored route points from last session if existant.
        fetchRoutePoints();
    }

    //Getters and Setters
    public static ArrayList<File> getGraphHopperFiles(){
        return ghFiles;
    }

    public void setGraphHopperFiles(ArrayList<File> ghFiles){
        this.ghFiles = ghFiles;
    }

    public GHPointArea getStartPoint() {
        return mStartPoint;
    }

    public void setStartPoint(GHPointArea ghPointArea) {
        mStartPoint = updateGHPointArea(mStartPoint, ghPointArea);

        markerStart = putMarkerItem(markerStart, mStartPoint.getGhPoint(), START_INDEX,
                R.string.departure, R.drawable.ic_place_green_24dp, -1);
        notifyWayPointSet(MarkerType.Departure, markerStart);
    }

    public GHPointArea getDestinationPoint() {
        return mDestinationPoint;
    }

    public void setDestinationPoint(GHPointArea ghPointArea) {
        mDestinationPoint = updateGHPointArea(mDestinationPoint, ghPointArea);

        markerDestination = putMarkerItem(markerDestination, mDestinationPoint.getGhPoint(), DEST_INDEX,
                R.string.destination,
                R.drawable.ic_place_red_24dp, -1);
        notifyWayPointSet(MarkerType.Destination, markerDestination);
    }

    public List<GHPointArea> getViaPoints() {
        return mViaPoints;
    }

    public void addViaPoint(GHPointArea ghPointArea) {
        GHPointAreaRoute.getInstance().add(ghPointArea, this);
        mViaPoints.add(ghPointArea);
        ExtendedMarkerItem item = putMarkerItem(null, ghPointArea.getGhPoint(), mViaPoints.size() - 1,
                R.string.viapoint, R.drawable.ic_place_yellow_24dp, -1);
        notifyWayPointSet(MarkerType.Via, item);
    }

    public void addNonRoutePoint(GHPointArea ghPointArea, Integer color) {
        mNonRoutePoints.add(ghPointArea);
        // Set color
        mNonRoutePointColors.add(color);

        // Set item
        ExtendedMarkerItem item = putMarkerItem(null, ghPointArea.getGhPoint(), (-(mNonRoutePoints.size() - 1)) - 3,
                R.string.poi, R.drawable.ic_place_white_24dp, -1, color);
        notifyWayPointSet(MarkerType.Other, item);

        // Center map
        centerMap(new GeoPoint(ghPointArea.getGhPoint().getLat(), ghPointArea.getGhPoint().getLon()));
    }

    public GHPointArea removePoint(int index) {
        GHPointArea removeElement = null;
        if (index == START_INDEX) {
            notifyWayPointRemoved(MarkerType.Departure, markerStart);
            GHPointAreaRoute.getInstance().remove(mStartPoint);
            removeElement = mStartPoint;
            mStartPoint = null;
            onGHPointUpdate(true);
        } else if (index == DEST_INDEX) {
            notifyWayPointRemoved(MarkerType.Destination, markerDestination);
            GHPointAreaRoute.getInstance().remove(mDestinationPoint);
            removeElement = mDestinationPoint;
            if (!mViaPoints.isEmpty()) {
                mDestinationPoint = removePoint(mViaPoints.size() - 1);
            } else {
                mDestinationPoint = null;
            }
            onGHPointUpdate(true);
        } else if (index < -2) {
            //Non route point markers
            int i = -(index + 3);
            if (mNonRoutePoints.size() > i) {
                notifyWayPointRemoved(MarkerType.Other, null);
                removeElement = mNonRoutePoints.remove(i);
                mNonRoutePointColors.remove(i);
                onGHPointUpdate(false);
            }
        } else {
            notifyWayPointRemoved(MarkerType.Via, null);
            GHPointAreaRoute.getInstance().getGHPointAreas().remove(mViaPoints.get(index));
            removeElement = mViaPoints.remove(index);
            onGHPointUpdate(true);
        }
        return removeElement;
    }

    /**
     * Retrieve route between p1 and p2 and update overlays.
     */
    public void showRoute(GeoPoint p1, GeoPoint p2) {
        clearOverlays();

        markerStart = putMarkerItem(markerStart, mStartPoint.getGhPoint(), START_INDEX,
                R.string.departure, R.drawable.ic_place_green_24dp, -1);
        mStartPoint = updateGHPointArea(mStartPoint, new GHPointArea(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(p1), ghFiles));
        mDestinationPoint = updateGHPointArea(mDestinationPoint, new GHPointArea(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(p2), ghFiles));

        markerDestination = putMarkerItem(markerDestination, mDestinationPoint.getGhPoint(), DEST_INDEX,
                R.string.destination,
                R.drawable.ic_place_red_24dp, -1);

    }

    private GHPointArea updateGHPointArea(GHPointArea oldGHPointArea, GHPointArea newGHPointArea){
        GHPointAreaRoute.getInstance().remove(oldGHPointArea);
        GHPointAreaRoute.getInstance().add(newGHPointArea, this);
        return newGHPointArea;
    }

    /**
     * Reverse Geocoding
     */
    public String getAddress(GeoPoint p) {
//        GeocoderNominatim geocoder = new GeocoderNominatim(activity);
//        String theAddress;
//        try {
//            double dLatitude = p.getLatitude();
//            double dLongitude = p.getLongitude();
//            List<Address> addresses = geocoder.getFromLocation(dLatitude, dLongitude, 1);
//            StringBuilder sb = new StringBuilder();
//            if (addresses.size() > 0) {
//                Address address = addresses.get(0);
//                int n = address.getMaxAddressLineIndex();
//                for (int i = 0; i <= n; i++) {
//                    if (i != 0)
//                        sb.append(", ");
//                    sb.append(address.getAddressLine(i));
//                }
//                theAddress = new String(sb.toString());
//            } else {
//                theAddress = null;
//            }
//        } catch (IOException e) {
//            theAddress = null;
//        }
//        if (theAddress != null) {
//            return theAddress;
//        }
        return "";
    }

    public Navigation getNavigation() {
        return mNavigation;
    }

    @Override
    public void onGHPointUpdate(boolean isRoutePoint) {
        if (isRoutePoint) {
            getRouteAsync();
        }
        updateIternaryMarkers();
    }

    public void storeRoutePoints() {
        List<LatLong> storeList = new ArrayList<>();
        GHPoint curP;
        if (mStartPoint != null) {
            curP = mStartPoint.getGhPoint();
            storeList.add(new LatLong(curP.getLat(), curP.getLon()));
        }
        for (GHPointArea mViaPoint : mViaPoints) {
            curP = mViaPoint.getGhPoint();
            storeList.add(new LatLong(curP.getLat(), curP.getLon()));
        }
        if (mDestinationPoint != null) {
            curP = mDestinationPoint.getGhPoint();
            storeList.add(new LatLong(curP.getLat(), curP.getLon()));
        }
        File destination = new File(StoragePreference.getPreferredStorageLocation().getAbsolutePath(),
                "/maps/route.list");
        LocationPersistenceManager.storeLocations(destination, storeList);
    }

    public void fetchRoutePoints() {
        File destination = new File(StoragePreference.getPreferredStorageLocation().getAbsolutePath(),
                "/maps/route.list");
        List<LatLong> storeList = LocationPersistenceManager.fetchLocations(destination);
        if (storeList == null) return;
        int i = 0;
        for (LatLong routePoint : storeList) {
            GHPointArea area = new GHPointArea(
                    new GHPoint(routePoint.getLatitude(), routePoint.getLongitude()), ghFiles);
            if (i == storeList.size() - 1) {
                setDestinationPoint(area);
            } else if (i == 0) {
                setStartPoint(area);
            } else {
                addViaPoint(area);
            }
            i++;
        }
    }

    // Async task to reverse-geocode the marker position in a separate thread:
    class GeocodingTask extends AsyncTask<Object, Void, String> {
        ExtendedMarkerItem marker;

        @Override
        protected String doInBackground(Object... params) {
            marker = (ExtendedMarkerItem) params[0];
            return getAddress(marker.getPoint());
        }

        @Override
        protected void onPostExecute(String result) {
            marker.setDescription(result);
        }
    }

    /* add (or replace) an item in markerOverlays. p position. */
    public synchronized ExtendedMarkerItem putMarkerItem(ExtendedMarkerItem item, GHPoint p, int index,
                                                         int titleResId, int markerResId, int iconResId) {
        return putMarkerItem(item, p, index, titleResId, markerResId, iconResId, null);
    }

    public synchronized ExtendedMarkerItem putMarkerItem(ExtendedMarkerItem item, GHPoint p, int index,
                                                         int titleResId, int markerResId, int iconResId, Integer colorResId) {

        if (item != null)
            mItineraryMarkers.removeItem(item);

        Drawable drawable = ContextCompat.getDrawable(activity, markerResId);
        if (colorResId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable.mutate(), ContextCompat.getColor(activity, colorResId));
        }
        Bitmap bitmap = AndroidGraphics.drawableToBitmap(drawable);
        MarkerSymbol marker = new MarkerSymbol(bitmap, 0.5f, 1);

        ExtendedMarkerItem overlayItem =
                new ExtendedMarkerItem(App.res.getString(titleResId), "", convertGHPointToGeoPoint(p));

        overlayItem.setMarker(marker);

        if (iconResId != -1) {
            overlayItem.setImage(ContextCompat.getDrawable(activity, iconResId));
        }

        overlayItem.setRelatedObject(index);

        mItineraryMarkers.addItem(overlayItem);

        App.map.updateMap(true);

        //Start geocoding task to update the description of the marker with its address:
        new GeocodingTask().execute(overlayItem);

        return overlayItem;
    }

    public void updateIternaryMarkers() {
        mItineraryMarkers.removeAllItems();

        //Start marker:
        if (mStartPoint != null) {
            markerStart = putMarkerItem(null, mStartPoint.getGhPoint(), START_INDEX,
                    R.string.departure, R.drawable.ic_place_green_24dp, -1);
        }
        //Via-points markers if any:
        for (int index = 0; index < mViaPoints.size(); index++) {
            putMarkerItem(null, mViaPoints.get(index).getGhPoint(), index,
                    R.string.viapoint, R.drawable.ic_place_yellow_24dp, -1);
        }
        //Destination marker if any:
        if (mDestinationPoint != null) {
            markerDestination = putMarkerItem(null, mDestinationPoint.getGhPoint(), DEST_INDEX,
                    R.string.destination,
                    R.drawable.ic_place_red_24dp, -1);
        }
        for (int index = 0; index < mNonRoutePoints.size(); index++) {
            GHPoint pt = mNonRoutePoints.get(index).getGhPoint();
            if (pt != null)
                putMarkerItem(null, pt, (-index) - 3,
                        R.string.poi, R.drawable.ic_place_white_24dp, -1, mNonRoutePointColors.get(index));
        }
        App.map.updateMap(true);
    }

    //------------ Route and Directions
    public void updateOverlays(PathWrapper route) {
        //mRouteMarkers.removeAllItems();

        mRouteOverlay.clearPath();

        if (route == null || route.hasErrors()) {
            activity.showToastOnUiThread(activity.getString(R.string.route_lookup_error));
            if (route != null)
                Log.e(activity.getString(R.string.route_lookup_error), route.getErrors().toString());
            return;
        }

        shortestPathRunning = false;


        mRouteOverlay.setPoints(convertPointListToGeoPoints(route.getPoints()));

        //OverlayMarker marker = AndroidGraphics.makeMarker(App.res, R.drawable.marker_node, null);

        //int n = route.nodes.size();
        //for (int i = 0; i < n; i++) {
        //    RouteNode node = route.nodes.get(i);
        //    String instructions = (node.instructions == null ? "" : node.instructions);
        //    ExtendedOverlayItem nodeMarker = new ExtendedOverlayItem(
        //            "Step " + (i + 1), instructions, node.location);
        //
        //    nodeMarker.setSubDescription(route.getLengthDurationText(node.length, node.duration));
        //    nodeMarker.setMarkerHotspot(OverlayItem.HotspotPlace.CENTER);
        //    nodeMarker.setMarker(marker);
        //
        //    mRouteMarkers.addItem(nodeMarker);
        //}

        //Set Route Bar infos
        mRouteBar.set(GraphhopperOsmdroidAdapter.convertPathWrapperToRoute(route));

        App.map.updateMap(true);
    }

    void clearOverlays() {
        mNavigation = null;
        //mRouteMarkers.removeAllItems(true);
        mItineraryMarkers.removeAllItems(true);

        mRouteOverlay.clearPath();
        //Must be cleared before removing destination index;
        for (int i = mViaPoints.size(); i > 0; i++) {
            removePoint(i - 1);
        }
        removePoint(START_INDEX);
        removePoint(DEST_INDEX);

        App.map.updateMap(true);
    }

    /**
     * Async task to get the route in a separate thread.
     */
    private class UpdateRouteTask extends AsyncTask<List<GHPointArea>, Void, List<GHResponse>> implements GHPointListener {
        float calctime;

        @Override
        protected List<GHResponse> doInBackground(List<GHPointArea>... wp) {
            List<GHPointArea> waypoints = wp[0];
            ArrayList<List<GHPointArea>> ghSubRouteList = new ArrayList<>();

            StopWatch sw = new StopWatch().start();
            //Split route in multiple GHPointLists
            for (int i = 0; i < waypoints.size(); i++) {
                if (i > 0) {
                    GraphHopper g1 = waypoints.get(i).getGraphHopper();
                    GraphHopper g2 = waypoints.get(i - 1).getGraphHopper();
                    if (g1 != null && g2 != null && !g1.equals(g2)) {
                        //Add element to last list of route points
                        ghSubRouteList.add(new ArrayList<GHPointArea>());
                    }
                    ghSubRouteList.get(ghSubRouteList.size() - 1).add(waypoints.get(i));
                } else {
                    if (ghSubRouteList.isEmpty()) {
                        ghSubRouteList.add(new ArrayList<GHPointArea>());
                    }
                    ghSubRouteList.get(0).add(waypoints.get(i));
                }
            }
            //Add Route points if necessary
            if (ghSubRouteList.isEmpty() || ghSubRouteList.get(0).isEmpty()) {
                return null;
            }
            for (int i = 0; i < ghSubRouteList.size(); i++) {
                if (i > 0) {
                    List<GHPointArea> subRoutes = ghSubRouteList.get(i);
                    if (!subRoutes.isEmpty()) {
                        List<GHPointArea> ghListBefore = ghSubRouteList.get(i - 1);
                        //Get last element of previousList
                        GHPointArea ghpaBefore = ghListBefore.get(ghListBefore.size()-1);
                        //Get first element of currentList
                        GHPointArea ghpaCurrent = subRoutes.get(0);
                        CrossMapCalculator calculator = new CrossMapCalculator(activity);
                        GHPoint crossPoint = calculator.getCrossPoint(ghpaBefore, ghpaCurrent);
                        if(crossPoint == null) return null;

                        GHPointArea ghPointAreaBefore = new GHPointArea(crossPoint,
                                ghpaBefore.getGraphHopper());
                        //Don't add to route list to avoid recursive updates
                        ghListBefore.add(ghPointAreaBefore);

                        GHPointArea ghPointAreaSub = new GHPointArea(crossPoint,
                                ghpaCurrent.getGraphHopper());
                        subRoutes.add(0, ghPointAreaSub);
                        //Don't add to route list to avoid recursive updates
                    }
                }
            }

            //Calculate Routes
            List<GHResponse> responses = new ArrayList<>();
            for (List<GHPointArea> pointList : ghSubRouteList) {
                GraphHopper hopper = pointList.get(0).getGraphHopper();
                if (hopper == null) return null;
                GHRequest req = new GHRequest(getRouteListOfAreaList(pointList)).
                        setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
                req.getHints().
                        put(Parameters.Routing.INSTRUCTIONS, "true");
                GHResponse resp = new GHResponse();
                List<Path> pathList = hopper.calcPaths(req, resp);
                if (!resp.getErrors().isEmpty()) {
                    return null;
                }
                responses.add(resp);
            }

            calctime = Math.round(sw.stop().getSeconds() * 100) / 100f;
            return responses;
        }

        @Override
        protected void onPostExecute(List<GHResponse> resp) {
            if(resp == null|| resp.isEmpty()){
                activity.showToastOnUiThread("Route calculation failed");
                return;
            }
            PathWrapper pathWrapper = new PathWrapper();
            long time = 0;
            List<String> description = new ArrayList<>();
            PointList pointList = new PointList();
            PointList wayPoints = new PointList();
            double distance = 0;
            double ascend = 0;
            double descend = 0;
            InstructionList instructionList = null;
            double routeWeight = 0;

            for(int i = 0; i<resp.size(); i++ ) {
                GHResponse re = resp.get(i);
                if (re == null || !re.getErrors().isEmpty()) {
                    activity.showToastOnUiThread("Route calculation failed. No routing data available");
                    return;
                }

                PathWrapper pw = re.getBest();

                time += pw.getTime();
                distance += pw.getDistance();
                descend += pw.getDescend();
                ascend += pw.getAscend();
                routeWeight += pw.getRouteWeight();
                wayPoints.add(pw.getWaypoints());
                pointList.add(pw.getPoints());
                try {
                    if (instructionList == null) {
                        instructionList = pw.getInstructions();
                        instructionList.clear();
                    }
                    for (Instruction in : pw.getInstructions()) {
                        instructionList.add(in);
                    }
                } catch (IllegalArgumentException ex){
                    Log.w("Graphh. Instructions", "Instructions disabled");
                }
                description.addAll(pw.getDescription());
            }
            pathWrapper.setAscend(ascend);
            pathWrapper.setDescend(descend);
            pathWrapper.setRouteWeight(routeWeight);
            pathWrapper.setDescription(description);
            pathWrapper.setPoints(pointList);
            pathWrapper.setDistance(distance);
            pathWrapper.setInstructions(instructionList);
            pathWrapper.setTime(time);
            pathWrapper.setWaypoints(wayPoints);

            activity.showToastOnUiThread("Route found in: " + calctime + " Seconds.");

            //Set Navigation
            App.activity.getLocationHandler().removeSnapLocationListener(mNavigation);
            mNavigation = new Navigation(pathWrapper);
            App.activity.getLocationHandler().addSnapLocationListener(mNavigation);

            updateOverlays(pathWrapper);

            mRouteTask = null;
            setActualRoute(pointList);

            // Center map to current route
            centerMap(null);
        }

        @Override
        public void onGHPointUpdate(boolean isRoutePoint) {
            //update
        }
    }

    public List<GHPoint> getRouteListOfAreaList(List<GHPointArea> areaPointList) {
        List<GHPoint> ghPoints = new ArrayList<>();
        for (GHPointArea element : areaPointList) {
            ghPoints.add(element.getGhPoint());
        }
        return ghPoints;
    }

    @SuppressWarnings("unchecked")
    public void getRouteAsync() {
        if (mRouteTask != null) {
            mRouteTask.cancel(true);
            mRouteTask = null;
        }

        if (mStartPoint == null || mDestinationPoint == null) {
            mRouteOverlay.clearPath();
            setActualRoute(null);
            return;
        }

        List<GHPointArea> waypoints = new ArrayList<GHPointArea>();
        waypoints.add(mStartPoint);
        //add intermediate via points:
        for (GHPointArea p : mViaPoints) {
            waypoints.add(p);
        }
        waypoints.add(mDestinationPoint);

        mRouteTask = new UpdateRouteTask();
        mRouteTask.execute(waypoints);
        activity.showToastOnUiThread("calculating path ...");
    }

    public void shareLocation(GHPoint poiLocation) {
        if (poiLocation == null) return;
        String uri = "geo:" + poiLocation.getLat() + ","
                + poiLocation.getLon() + "?q=" + poiLocation.getLat()
                + "," + poiLocation.getLon();
        activity.startActivity(new Intent(android.content.Intent.ACTION_VIEW,
                Uri.parse(uri)));
    }

    /**
     * handle action of items, popped up with long press on map
     */
    public boolean onContextItemSelected(MenuItem item, GeoPoint geoPoint) {
        switch (item.getItemId()) {
            case R.id.menu_share_location:
                shareLocation(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(geoPoint));
                return true;

            case R.id.menu_route_departure:
                setStartPoint(new GHPointArea(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(geoPoint), ghFiles));
                return true;

            case R.id.menu_route_destination:
                setDestinationPoint(new GHPointArea(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(geoPoint), ghFiles));
                return true;

            case R.id.menu_route_viapoint:
                GHPointArea viaPoint = new GHPointArea(OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint(geoPoint), ghFiles);
                addViaPoint(viaPoint);
                return true;

            case R.id.menu_route_clear:
                GHPointAreaRoute.getInstance().setGHPointAreas(null);
                clearOverlays();
                return true;

            default:
        }
        return false;
    }

    public boolean isEmpty() {
        return (mItineraryMarkers.size() == 0);
    }

    class ViaPointInfoWindow extends DefaultInfoWindow {

        int mSelectedPoint;

        public ViaPointInfoWindow(int layoutResId) {
            super(layoutResId, App.view);

            Button btnDelete = (Button) (mView.findViewById(R.id.bubble_delete));
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removePoint(mSelectedPoint);
                    close();
                }
            });
        }

        @Override
        public void onOpen(ExtendedMarkerItem item) {
            mSelectedPoint = ((Integer) item.getRelatedObject()).intValue();
            super.onOpen(item);
        }
    }

    class RouteBar {

        TextView mDistance = null;
        TextView mRouteLength = null;
        TextView mTravelTime = null;
        TextView mText = null;
        TextView mMaxSpeed = null;
        ImageView mClearButton = null;
        RelativeLayout mRouteBarView = null;

        RouteBar(Activity activity) {

            mRouteBarView = (RelativeLayout) activity.findViewById(R.id.route_bar);
            mDistance = (TextView) activity.findViewById(R.id.route_bar_distance);
            mRouteLength = (TextView) activity.findViewById(R.id.route_bar_route_length);
            mTravelTime = (TextView) activity.findViewById(R.id.route_bar_travel_time);
            mText = (TextView) activity.findViewById(R.id.route_bar_text);
            mMaxSpeed = (TextView) activity.findViewById(R.id.route_bar_maxspeed);

            mClearButton = (ImageView) activity.findViewById(R.id.route_bar_clear);

            mRouteBarView.setVisibility(View.INVISIBLE);

            mClearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mRouteBarView.setVisibility(View.INVISIBLE);
                    clearOverlays();
                }
            });
        }

        public void set(Route result) {
            DecimalFormat twoDForm = new DecimalFormat("#.#");
            DecimalFormat oneDForm = new DecimalFormat("#");
            int hour = ((int) result.duration / 3600);
            int minute = ((int) result.duration % 3600) / 60;
            String time = "";
            if (hour == 0 && minute == 0) {
                time = "?";
            } else if (hour == 0 && minute != 0) {
                time = minute + "m";
            } else {
                time = hour + "h " + minute + "m";
            }

            List<GeoPoint> routeLow = result.getRouteLow();
            double dis = 0;
            if (routeLow.size() > 1)
                dis = routeLow.get(0).sphericalDistance(routeLow.get(routeLow.size() - 1)) / 1000;
            String distance;
            String shortpath;
            if (dis < 100) {
                distance = twoDForm.format(dis);
            } else {
                distance = oneDForm.format(dis);
            }
            if (result.length == 0) {
                shortpath = "?";
            } else if (result.length < 100) {
                shortpath = twoDForm.format(result.length);
            } else {
                shortpath = oneDForm.format(result.length);
            }

            Instruction lastIns = mNavigation.getLastInstruction();
            if (lastIns != null) {
                if (!(lastIns.getDistance() == 0)) ;
                mMaxSpeed.setText(Math.round(lastIns.getDistance() / (lastIns.getTime() / 3600.)) + " km/h");
                if (!lastIns.getName().isEmpty())
                    mText.setText(lastIns.getName());
            }
            mRouteBarView.setVisibility(View.VISIBLE);
            mDistance.setText(distance + " km");
            mTravelTime.setText(time);
            mRouteLength.setText(shortpath + " km");
        }
    }

    public PointList getActualRoute() {
        return mActualRoute;
    }

    public void setActualRoute(PointList actualRoute) {
        this.mActualRoute = actualRoute;
    }

    private void centerMap(GeoPoint latLong) {
        if (latLong == null) {
            if (mActualRoute == null) return;
            Iterator<GHPoint3D> iterator = mActualRoute.iterator();
            BoundingBox bbox = null;
            while (iterator.hasNext()) {
                GHPoint3D ghp = iterator.next();
                double lat = ghp.getLat();
                double lon = ghp.getLon();
                if (bbox == null) {
                    bbox = new BoundingBox(lat, lon, lat, lon);
                } else {
                    bbox = bbox.extendCoordinates(lat, lon);
                }
            }
            if (bbox != null) {
                bbox = bbox.extendMargin(1.5f);

                App.map.animator().animateTo(new org.oscim.core.BoundingBox(
                        bbox.minLatitude, bbox.minLongitude,
                        bbox.maxLatitude, bbox.maxLongitude));
            }
        } else {
            App.map.animator().animateTo(latLong);
        }
    }

    private Collection<RouteSearchListener> routeSearchListeners = new HashSet<RouteSearchListener>();

    public void addRouteSearchListener(RouteSearchListener listener) {
        routeSearchListeners.add(listener);
    }

    public void removeRouteSearchListener(RouteSearchListener listener) {
        routeSearchListeners.remove(listener);
    }

    public interface RouteSearchListener {
        public void onWaypointSet(MarkerType type, ExtendedMarkerItem item);

        public void onWaypointRemoved(MarkerType type, ExtendedMarkerItem item);
    }

    public void notifyWayPointSet(MarkerType type, ExtendedMarkerItem item) {
        for (RouteSearchListener routeSearchListener : routeSearchListeners) {
            routeSearchListener.onWaypointSet(type, item);
        }
    }

    public void notifyWayPointRemoved(MarkerType type, ExtendedMarkerItem item) {
        for (RouteSearchListener routeSearchListener : routeSearchListeners) {
            routeSearchListener.onWaypointRemoved(type, item);
        }
    }

    public enum MarkerType {
        Departure,
        Via,
        Destination,
        Poi,
        Other
    }
}


