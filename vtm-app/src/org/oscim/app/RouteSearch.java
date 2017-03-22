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
package org.oscim.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
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
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;

import org.oscim.android.canvas.AndroidGraphics;
import org.oscim.app.graphhopper.CrossMapCalculator;
import org.oscim.app.graphhopper.GHPointArea;
import org.oscim.app.graphhopper.GHPointAreaRoute;
import org.oscim.app.graphhopper.GHPointListener;
import org.oscim.app.graphhopper.GraphhopperOsmdroidAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.backend.canvas.Paint;
import org.oscim.core.GeoPoint;
import org.oscim.layers.PathLayer;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.theme.styles.LineStyle;
import org.osmdroid.location.GeocoderNominatim;
import org.osmdroid.overlays.DefaultInfoWindow;
import org.osmdroid.overlays.ExtendedMarkerItem;
import org.osmdroid.overlays.ItemizedOverlayWithBubble;
import org.osmdroid.routing.Route;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static org.oscim.app.graphhopper.GraphhopperOsmdroidAdapter.convertGHPointToGeoPoint;
import static org.oscim.app.graphhopper.GraphhopperOsmdroidAdapter.convertPointListToGeoPoints;
import static org.oscim.app.graphhopper.OsmdroidGraphhopperAdapter.convertGeoPointToGHPoint;

public class RouteSearch implements GHPointListener {
    private static int START_INDEX = -2, DEST_INDEX = -1;

    private final PathLayer mRouteOverlay;
    //private final ItemizedOverlayWithBubble<ExtendedOverlayItem> mRouteMarkers;
    private final ItemizedOverlayWithBubble<ExtendedMarkerItem> mItineraryMarkers;

    private final RouteBar mRouteBar;

    private GHPointArea mStartPoint, mDestinationPoint;
    private final ArrayList<GHPointArea> mViaPoints;

    private ExtendedMarkerItem markerStart, markerDestination;

    private UpdateRouteTask mRouteTask;

    private static ArrayList<File> ghFiles;

    //private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;

    RouteSearch() {
        mViaPoints = new ArrayList<GHPointArea>();

        // Itinerary markers:
        ArrayList<ExtendedMarkerItem> waypointsItems = new ArrayList<ExtendedMarkerItem>();

        mItineraryMarkers = new ItemizedOverlayWithBubble<ExtendedMarkerItem>(App.map,
                App.activity,
                null,
                waypointsItems,
                new ViaPointInfoWindow(R.layout.itinerary_bubble));

        //updateIternaryMarkers();

        //Route and Directions
        //ArrayList<ExtendedOverlayItem> routeItems = new ArrayList<ExtendedOverlayItem>();
        //mRouteMarkers = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(App.map, App.activity,
        //        null, routeItems);
        LineStyle routeLine = new LineStyle(Color.parseColor("#CC3377FF"), 14, Paint.Cap.ROUND);
        mRouteOverlay = new PathLayer(App.map, routeLine);

        // TODO use LayerGroup
        App.map.layers().add(mRouteOverlay);
        //App.map.getOverlays().add(mRouteMarkers);
        App.map.layers().add(mItineraryMarkers);

        mRouteBar = new RouteBar(App.activity);

        ghFiles = FileUtils.walkExtension(MapLayers.MAP_FOLDER, "-gh");
        ghFiles.add(MapLayers.MAP_FOLDER); //Add default folder for loading unzipped GH-Files
    }

    public static ArrayList<File> getGraphHopperFiles(){
        return ghFiles;
    }

    public void setGraphHopperFiles(ArrayList<File> ghFiles){
        this.ghFiles = ghFiles;
    }

    /**
     * Retrieve route between p1 and p2 and update overlays.
     */
    public void showRoute(GeoPoint p1, GeoPoint p2) {
        clearOverlays();

        markerStart = putMarkerItem(markerStart, mStartPoint.getGhPoint(), START_INDEX,
                R.string.departure, R.drawable.ic_place_green_24dp, -1);
        mStartPoint = updateGHPointArea(mStartPoint, new GHPointArea(convertGeoPointToGHPoint(p1), ghFiles));
        mDestinationPoint = updateGHPointArea(mDestinationPoint, new GHPointArea(convertGeoPointToGHPoint(p2), ghFiles));

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
        GeocoderNominatim geocoder = new GeocoderNominatim(App.activity);
        String theAddress;
        try {
            double dLatitude = p.getLatitude();
            double dLongitude = p.getLongitude();
            List<Address> addresses = geocoder.getFromLocation(dLatitude, dLongitude, 1);
            StringBuilder sb = new StringBuilder();
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                int n = address.getMaxAddressLineIndex();
                for (int i = 0; i <= n; i++) {
                    if (i != 0)
                        sb.append(", ");
                    sb.append(address.getAddressLine(i));
                }
                theAddress = new String(sb.toString());
            } else {
                theAddress = null;
            }
        } catch (IOException e) {
            theAddress = null;
        }
        if (theAddress != null) {
            return theAddress;
        }
        return "";
    }

    @Override
    public void onRoutePointUpdate() {
        getRouteAsync();
        updateIternaryMarkers();
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
    public ExtendedMarkerItem putMarkerItem(ExtendedMarkerItem item, GHPoint p, int index,
                                            int titleResId, int markerResId, int iconResId) {

        if (item != null)
            mItineraryMarkers.removeItem(item);

        Drawable drawable = ContextCompat.getDrawable(App.activity, markerResId);
        Bitmap bitmap = AndroidGraphics.drawableToBitmap(drawable);
        MarkerSymbol marker = new MarkerSymbol(bitmap, 0.5f, 1);

        ExtendedMarkerItem overlayItem =
                new ExtendedMarkerItem(App.res.getString(titleResId), "", convertGHPointToGeoPoint(p));

        overlayItem.setMarker(marker);

        if (iconResId != -1) {
            overlayItem.setImage(ContextCompat.getDrawable(App.activity, iconResId));
        }

        overlayItem.setRelatedObject(index);

        mItineraryMarkers.addItem(overlayItem);

        App.map.updateMap(true);

        //Start geocoding task to update the description of the marker with its address:
        new GeocodingTask().execute(overlayItem);

        return overlayItem;
    }

    public void addViaPoint(GHPointArea p) {
        mViaPoints.add(p);
        putMarkerItem(null, p.getGhPoint(), mViaPoints.size() - 1,
                R.string.viapoint, R.drawable.ic_place_yellow_24dp, -1);
    }

    public void removePoint(int index) {
        if (index == START_INDEX) {
            GHPointAreaRoute.getInstance().remove(mStartPoint);
            mStartPoint = null;
        } else if (index == DEST_INDEX) {
            GHPointAreaRoute.getInstance().remove(mDestinationPoint);
            mDestinationPoint = null;
        } else {
            GHPointAreaRoute.getInstance().getGHPointAreas().remove(mViaPoints.get(index));
            mViaPoints.remove(index);
        }
        onRoutePointUpdate();
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
        App.map.updateMap(true);
    }

    //------------ Route and Directions
    private void updateOverlays(PathWrapper route) {
        //mRouteMarkers.removeAllItems();

        mRouteOverlay.clearPath();

        if (route == null || route.hasErrors()) {
            App.activity.showToastOnUiThread(App.activity.getString(R.string.route_lookup_error));
            if (route != null)
                Log.e(App.activity.getString(R.string.route_lookup_error), route.getErrors().toString());
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

        App.map.updateMap(true);
    }

    void clearOverlays() {
        //mRouteMarkers.removeAllItems(true);
        mItineraryMarkers.removeAllItems(true);

        mRouteOverlay.clearPath();
        mStartPoint = null;
        mDestinationPoint = null;
        mViaPoints.clear();

        App.map.updateMap(true);
    }

    /**
     * Async task to get the route in a separate thread.
     */
    class UpdateRouteTask extends AsyncTask<List<GHPointArea>, Void, List<GHResponse>> implements GHPointListener {
        float time;

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
                        CrossMapCalculator calculator = new CrossMapCalculator(App.activity);
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
                        put(Parameters.Routing.INSTRUCTIONS, "false");
                GHResponse resp = hopper.route(req);
                if (!resp.getErrors().isEmpty()) {
                    return null;
                }
                responses.add(resp);
            }

            time = sw.stop().getSeconds();
            return responses;
        }

        @Override
        protected void onPostExecute(List<GHResponse> resp) {
            if(resp == null|| resp.isEmpty()){
                App.activity.showToastOnUiThread("Route calculation failed");
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
            InstructionList instructionList = InstructionList.EMPTY;
            double routeWeight = 0;

            for(int i = 0; i<resp.size(); i++ ) {
                GHResponse re = resp.get(i);
                if (re == null || !re.getErrors().isEmpty()) {
                    App.activity.showToastOnUiThread("Route calculation failed. No routing data available");
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
                    for (Instruction in : pw.getInstructions()) {
                        instructionList.add(in);
                    }
                } catch (IllegalArgumentException ex){
                    Log.w("Graphhopper Instructions", "Instructions disabled");
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

            App.activity.showToastOnUiThread("Route found in: " + time + " Seconds.");
            updateOverlays(pathWrapper);

            mRouteBar.set(GraphhopperOsmdroidAdapter.convertPathWrapperToRoute(pathWrapper));

            mRouteTask = null;
        }

        @Override
        public void onRoutePointUpdate() {
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
        App.activity.showToastOnUiThread("calculating path ...");
    }

    /**
     * handle action of items, popped up with long press on map
     */
    boolean onContextItemSelected(MenuItem item, GeoPoint geoPoint) {
        switch (item.getItemId()) {
            case R.id.menu_route_departure:
                mStartPoint = updateGHPointArea(mStartPoint,
                        new GHPointArea(convertGeoPointToGHPoint(geoPoint), ghFiles));

                markerStart = putMarkerItem(markerStart, mStartPoint.getGhPoint(), START_INDEX,
                        R.string.departure, R.drawable.ic_place_green_24dp, -1);
                return true;

            case R.id.menu_route_destination:
                mDestinationPoint = updateGHPointArea(mDestinationPoint,
                        new GHPointArea(convertGeoPointToGHPoint(geoPoint), ghFiles));

                markerDestination = putMarkerItem(markerDestination, mDestinationPoint.getGhPoint(), DEST_INDEX,
                        R.string.destination,
                        R.drawable.ic_place_red_24dp, -1);
                return true;

            case R.id.menu_route_viapoint:
                GHPointArea viaPoint = new GHPointArea(convertGeoPointToGHPoint(geoPoint), ghFiles);
                GHPointAreaRoute.getInstance().add(viaPoint, this);
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
        ImageView mClearButton = null;
        RelativeLayout mRouteBarView = null;

        RouteBar(Activity activity) {

            mRouteBarView = (RelativeLayout) activity.findViewById(R.id.route_bar);
            mDistance = (TextView) activity.findViewById(R.id.route_bar_distance);
            mRouteLength = (TextView) activity.findViewById(R.id.route_bar_route_length);
            mTravelTime = (TextView) activity.findViewById(R.id.route_bar_travel_time);

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

            double dis = convertGHPointToGeoPoint(mStartPoint.getGhPoint())
                    .sphericalDistance(convertGHPointToGeoPoint(mDestinationPoint.getGhPoint())) / 1000;
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

            mRouteBarView.setVisibility(View.VISIBLE);
            mDistance.setText(distance + " km");
            mTravelTime.setText(time);
            mRouteLength.setText(shortpath + " km");
        }
    }
}


