/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2013 Hannes Janetzek
 * Copyright 2013 Ahmad Al-saleem
 * Copyright 2016 devemux86
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
package org.oscim.app.location;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.os.AsyncTaskCompat;
import android.view.animation.LinearInterpolator;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.math.Vector2D;

import org.oscim.app.App;
import org.oscim.app.R;
import org.oscim.app.RouteSearch;
import org.oscim.app.TileMap;
import org.oscim.app.graphhopper.GHPointArea;
import org.oscim.core.MapPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static org.oscim.app.App.map;
import static org.oscim.app.graphhopper.GraphhopperOsmdroidAdapter.convertGHPointToGeoPoint;

public class LocationHandler implements LocationListener {

    private static final double MAX_GPS_DISTANCE_DEVIATION = 15;

    public PointList getActualRoute() {
        return actualRoute;
    }

    public void setActualRoute(PointList actualRoute) {
        this.actualRoute = actualRoute;
    }

    public enum Mode {
        OFF,
        SHOW,
        SNAP,
        NAV
    }

    private final static int DIALOG_LOCATION_PROVIDER_DISABLED = 2;
    private final static int SHOW_LOCATION_ZOOM = 14;
    private final static int NAVIGATION_ZOOM = 20;
    private final static int GPS_MINIMUM_DISTANCE = 10; //Standard 10
    private final static int GPS_MINIMUM_TIME_ELAPSE = 5000; //Standard 10000

    private final LocationManager mLocationManager;
    private final LocationLayerImpl mLocationLayer;

    private Mode mMode = Mode.OFF;

    private boolean mSetCenter;
    private MapPosition mMapPosition;
    private PointList actualRoute = null;

    public LocationHandler(TileMap tileMap, Compass compass) {
        mLocationManager = (LocationManager) tileMap
                .getSystemService(Context.LOCATION_SERVICE);

        mLocationLayer = new LocationLayerImpl(map, compass);

        mMapPosition = new MapPosition();
    }

    public boolean setMode(Mode mode) {
        if (mode == mMode)
            return true;

        if (mode == Mode.OFF) {
            disableShowMyLocation();

            if (mMode == Mode.SNAP || mMode == Mode.NAV)
                map.getEventLayer().enableMove(true);
        }

        if (mMode == Mode.OFF) {
            if (!enableShowMyLocation())
                return false;
        }

        if (mode == Mode.SNAP) {
            map.getEventLayer().enableMove(false);
            gotoLastKnownPosition(SHOW_LOCATION_ZOOM);
        } else if (mode == Mode.NAV) {
            map.getEventLayer().enableMove(false);
            gotoLastKnownPosition(NAVIGATION_ZOOM);
        } else {
            map.getEventLayer().enableMove(true);
        }

        // FIXME?
        mSetCenter = false;
        mMode = mode;

        return true;
    }

    public Mode getMode() {
        return mMode;
    }

    public boolean isFirstCenter() {
        return mSetCenter;
    }

    @SuppressWarnings("deprecation")
    private boolean enableShowMyLocation() throws SecurityException {

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String bestProvider = mLocationManager.getBestProvider(criteria, true);

        if (bestProvider == null) {
            App.activity.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
            return false;
        }

        mLocationManager.requestLocationUpdates(bestProvider, GPS_MINIMUM_TIME_ELAPSE,
                GPS_MINIMUM_DISTANCE, this);

        Location location = gotoLastKnownPosition(SHOW_LOCATION_ZOOM);
        if (location == null)
            return false;

        initGraphHopperLocation(location.getLatitude(), location.getLongitude());
        //Set start point if not set yet
        if ((App.routeSearch.getStartPoint() == null)
                && (App.routeSearch.getDestinationPoint() != null)) {
            App.routeSearch.setStartPoint(new GHPointArea(
                    new GHPoint(location.getLatitude(), location.getLongitude()),
                    RouteSearch.getGraphHopperFiles()));
        }

        //Handle Showing position
        mLocationLayer.setEnabled(true);
        mLocationLayer.setPosition(location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy());

        // FIXME -> implement LayerGroup
        map.layers().add(4, mLocationLayer);

        map.updateMap(true);
        return true;
    }

    /**
     * Disable "show my location" mode.
     */
    private boolean disableShowMyLocation() {

        mLocationManager.removeUpdates(this);
        mLocationLayer.setEnabled(false);

        map.layers().remove(mLocationLayer);
        map.updateMap(true);

        return true;
    }

    public Location gotoLastKnownPosition(int zoomLevel) throws SecurityException {
        Location location = null;
        float bestAccuracy = Float.MAX_VALUE;

        for (String provider : mLocationManager.getProviders(true)) {
            Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null)
                continue;

            float accuracy = l.getAccuracy();
            if (accuracy <= 0)
                accuracy = Float.MAX_VALUE;

            if (location == null || accuracy <= bestAccuracy) {
                location = l;
                bestAccuracy = accuracy;
            }
        }

        if (location == null) {
            App.activity.showToastOnUiThread(App.activity
                    .getString(R.string.error_last_location_unknown));
            return null;
        }

        map.getMapPosition(mMapPosition);

        if (mMapPosition.zoomLevel < zoomLevel)
            mMapPosition.setZoomLevel(zoomLevel);

        double lat = location.getLatitude();
        double lon = location.getLongitude();

//        //Set Location to bottom for navigation
//        double radian = Math.toRadians(location.getBearing());
//        double y = Math.sin(radian);
//        double x = Math.cos(radian);
//        Vector2D vector = new Vector2D(x, y);
//        vector = vector.divide(vector.length()); //Unit vector
//        double distance = App.map.getMapPosition().getTilt() / 100;
//        vector = vector.multiply(0.001 * distance); //Distance Value (dependent on the map tilt)
//        lat += vector.getX();
//        lon = vector.getY();

        //Set Map position
        mMapPosition.setPosition(lat, lon);
        App.map.setMapPosition(mMapPosition);

        return location;
    }

    /***
     * LocationListener
     ***/

    public void onVirtualLocationChanged(Location location) {
        //Inform Compass about big location changes
        for (LocationListener hl : virtualLocationListeners)
            hl.onLocationChanged(location);

        if (mMode == Mode.OFF)
            return;

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        //log.debug("update location " + lat + ":" + lon);

        if (mSetCenter || mMode == Mode.SNAP || mMode == Mode.NAV) {
            mSetCenter = false;

            map.getMapPosition(mMapPosition);
            mMapPosition.setPosition(lat, lon);
            map.setMapPosition(mMapPosition);
        }

        mLocationLayer.setPosition(lat, lon, location.getAccuracy());
    }

    private void initGraphHopperLocation(double lat, double lon){
        //Load Graphhopper asynchroniously, if is null
        AsyncTask task = new AsyncTask<Object, Void, Void>() {
            @Override
            protected Void doInBackground(Object[] params) {
                try {
                    GHPointArea area = new GHPointArea(
                            new GHPoint((double) params[0], (double) params[1])
                            , RouteSearch.getGraphHopperFiles());
                    if (area.getGraphHopper() == null) {
                        synchronized (area.virtualObject) {
                            try {
                                area.virtualObject.wait();
                                App.activity.showToastOnUiThread("Way snap initialized");
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    App.activity.showToastOnUiThread(e.getMessage());
                }
                return null;
            }
        };
        AsyncTaskCompat.executeParallel(task, lat, lon);
        //App.activity.showToastOnUiThread("Way animation in progress");
    }

    private synchronized ArrayList<Location> decideVirtualPath(Location preLocation, Location currentLocation) {
        if (getActualRoute() == null) {
            App.activity.showToastOnUiThread("Predict path");
            return predictVirtualPath(preLocation, currentLocation);
        } else {
            return calcVirtualPointsOnPath(currentLocation);
        }
    }

    private GraphHopper locationGh;

    /**
     * Predict next location, if there is no given way.
     *
     * @param preLocation
     * @param currentLocation
     * @return List of Locations, which could be the next ones.
     */
    private synchronized ArrayList<Location> predictVirtualPath(Location preLocation, Location currentLocation) {
        //TODO Add File list for better results
        double curLat = currentLocation.getLatitude();
        double curLon = currentLocation.getLongitude();

        double diffLat = curLat - preLocation.getLatitude();
        double diffLon = curLon - preLocation.getLongitude();
        double abs = new Vector2D(diffLat, diffLon).length();

        Location locationEnd = new Location(currentLocation);
        Location locationStart = new Location(currentLocation);

        if (locationGh == null) {
            App.activity.showToastOnUiThread("No GraphHopper matches the point or it is loading");
            AsyncTask<Object, Void, Void> task = new AsyncTask<Object, Void, Void>() {
                @Override
                protected Void doInBackground(Object[] params) {
                    GHPointArea tempPointArea = new GHPointArea(new GHPoint(
                            (double) params[0], (double) params[1]),
                            RouteSearch.getGraphHopperFiles());
                    if (tempPointArea.getGraphHopper() == null) {
                        synchronized (tempPointArea.virtualObject) {
                            try {
                                // Calling wait() will block this thread until another thread
                                // calls notify() on the object.
                                tempPointArea.virtualObject.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    locationGh = tempPointArea.getGraphHopper();
                    App.activity.showToastOnUiThread("GraphHopper loaded");
                    return null;
                }
            };
            AsyncTaskCompat.executeParallel(task, curLat, curLon);
            return null;
        }

        int curNode = locationGh.getLocationIndex().findClosest(curLat, curLon,
                EdgeFilter.ALL_EDGES).getClosestNode();
        if (curNode < 1) {
            App.activity.showToastOnUiThread("Closest calculated point not existent. " +
                    "Recalculate GraphHopper...");
            locationGh = new GHPointArea(new GHPoint(curLat, curLon),
                    RouteSearch.getGraphHopperFiles()).getGraphHopper();
            return null;
        }
        NodeAccess na = locationGh.getGraphHopperStorage().getNodeAccess();
        Graph graph = locationGh.getGraphHopperStorage().getBaseGraph();
        EdgeExplorer explorer = graph.createEdgeExplorer(EdgeFilter.ALL_EDGES);
        EdgeIterator iter = explorer.setBaseNode(curNode);
        int nextNode = 0;
        while (iter.next()) {
            double tmpLat = na.getLatitude(iter.getAdjNode());
            double tmpLon = na.getLongitude(iter.getAdjNode());
            float preAngle = (float) Math.atan2(diffLat, diffLon);
            float tmpAngle = (float) Math.atan2(curLat - tmpLat, curLon - tmpLon);
            if (Math.abs((tmpAngle - preAngle + Math.PI) % (2 * Math.PI)) < Math.PI / 2) {
                nextNode = iter.getAdjNode();
                break;
            }
        }
        if (nextNode < 1) return null;
//        QueryGraph queryGraph = new QueryGraph(graph);
//        queryGraph.lookup(curNode, nextNode);
        //TODO Improve waypoint calculation by using virtual nodes
        //Normalize point
        diffLat = na.getLatitude(nextNode) - curLat;
        diffLon = na.getLongitude(nextNode) - curLon;

        Vector2D vec = new Vector2D(diffLon, diffLat);
        vec = vec.divide(vec.length()).multiply(abs);

        locationEnd.setLatitude(curLat + vec.getY());
        locationEnd.setLongitude(curLon + vec.getX());

        ArrayList<Location> result = new ArrayList<>();
        result.add(locationStart);
        result.add(locationEnd);
        //App.activity.showToastOnUiThread("Next location calculated");
        return result;
    }

    private ArrayList<Location> calcVirtualPointsOnPath(Location currentLocation) {
//        App.activity.showToastOnUiThread("Stop 1");
        double curLat = currentLocation.getLatitude();
        double curLon = currentLocation.getLongitude();
        double distance = Double.MAX_VALUE;

        Iterator<GHPoint3D> iterator = actualRoute.iterator();
        boolean foundNearest = false;
        PointList drawPoints = new PointList();
        GHPoint secNearestPoint = null;
        GHPoint nearestPoint = null;
        GHPoint afterPoint = null;
        int i = 0;
        int secIndex = -1;
        while (iterator.hasNext()) {
            GHPoint next = iterator.next();
            double actDis = convertGHPointToGeoPoint(next)
                    .sphericalDistance(convertGHPointToGeoPoint(new GHPoint(curLat, curLon))); //in meters
            if (actDis < distance) {
                foundNearest = false;
                distance = actDis;
                secNearestPoint = nearestPoint;
                nearestPoint = next;
                secIndex = (i == 0) ? 0 : (i - 1);
            } else if (!foundNearest) {
                foundNearest = true;
                afterPoint = next;
            }
            i++;
        }
        if (secIndex != -1) {
            drawPoints = actualRoute.copy(secIndex, actualRoute.size() - 1);
        } else {
            return null;
        }


        if (drawPoints.isEmpty()) actualRoute = null;

//        App.activity.showToastOnUiThread("Stop 2");
        if (nearestPoint == null) return null;
        Coordinate curLoc = new Coordinate(curLon, curLat);
        Coordinate nearLoc = new Coordinate(nearestPoint.getLon(), nearestPoint.getLat());

        Coordinate first = null;
        Coordinate second = null;
        double firstDistance = Double.MAX_VALUE;
        double secDistance = Double.MAX_VALUE;
        if (secNearestPoint != null) {
            Coordinate secNearLoc = new Coordinate(secNearestPoint.getLon(), secNearestPoint.getLat());
            first = projectCoordinate(secNearLoc, nearLoc, curLoc);
            firstDistance = curLoc.distance(first);
        }
        if (afterPoint != null) {
            Coordinate afterLoc = new Coordinate(afterPoint.getLon(), afterPoint.getLat());
            second = projectCoordinate(nearLoc, afterLoc, curLoc);
            secDistance = curLoc.distance(second);
        }
        Coordinate startCoord = null;
        if (firstDistance < secDistance) {
            if (firstDistance > MAX_GPS_DISTANCE_DEVIATION) {
                return null;
            }
            startCoord = first;
        } else {
            if (secDistance > MAX_GPS_DISTANCE_DEVIATION) {
                return null;
            }
            drawPoints = drawPoints.copy(1, drawPoints.getSize() - 1);
            startCoord = second;
        }

//        App.activity.showToastOnUiThread("Stop 3");
        if (startCoord == null) return null;

        actualRoute = drawPoints;
        if (drawPoints.isEmpty()) return null;
        drawPoints.set(0, startCoord.y, startCoord.x, startCoord.z);

        iterator = drawPoints.iterator();
        ArrayList<Location> locations = new ArrayList<>();
        Location tmp = null;
        //Add points and calculate bearing;
        while (iterator.hasNext()) {
            GHPoint pt = iterator.next();
            Location l = new Location(currentLocation);
            l.setLongitude(pt.getLon());
            l.setLatitude(pt.getLat());
            if (tmp != null) {
                tmp.setBearing(tmp.bearingTo(l));
                locations.add(tmp);
            }
            tmp = l;
        }
        if (tmp != null) {
            if (!locations.isEmpty()) {
                //Set Bearing for last point
                tmp.setBearing(locations.get(locations.size() - 1).getBearing());
            }
            locations.add(tmp);
        }
        return locations;
    }

    private Coordinate projectCoordinate(Coordinate a, Coordinate b, Coordinate p) {
        Coordinate c = new Coordinate(b.x - a.x, b.y - a.y);

        double lamda = ((p.x - a.x) * c.x + (p.y - a.y) * c.y) / ((c.x * c.x) + (c.y * c.y));
        Coordinate r;
        if (lamda < 0) {
            r = a;
        } else {
            r = new Coordinate(a.x + lamda * c.x, a.y + lamda * c.y);
            if (a.distance(b) < a.distance(r)) {
                r = b;
            }
        }

        return r;
    }

    public Location calculateProgressLocation(Location startLocation, Location endLocation, float progress) {
        Location location = new Location(startLocation);
        double diffLat = endLocation.getLatitude() - startLocation.getLatitude();
        double diffLon = endLocation.getLongitude() - startLocation.getLongitude();
        float diffBearing = endLocation.getBearing() - startLocation.getBearing();
        long diffTime = endLocation.getTime() - startLocation.getTime();
        float diffSpeed = endLocation.getSpeed() - startLocation.getSpeed();
        if (diffLat != 0) location.setLatitude(location.getLatitude() + diffLat * progress);
        if (diffLon != 0) location.setLongitude(location.getLongitude() + diffLon * progress);
        if (diffBearing != 0f) location.setBearing(location.getBearing() + diffBearing * progress);
        if (diffTime != 0) location.setTime(location.getTime() + (long) (diffTime * progress));
        if (diffSpeed != 0f) location.setSpeed(location.getSpeed() + diffSpeed * progress);

        //App.activity.showToastOnUiThread("Calculate Progress: " + progress);
        return location;
    }

    private ValueAnimator anim;
    private Location preLocation;
    @Override
    public void onLocationChanged(final Location location) {
        App.activity.showToastOnUiThread("Location changed");
        if (preLocation == null) {
//            App.activity.showToastOnUiThread("Prelocation is null");
            onVirtualLocationChanged(location);
            preLocation = location;
            return;
        }
        //Animate big rotation steps
        //App.activity.showToastOnUiThread("Init location calculation");
        final ArrayList<Location> path = decideVirtualPath(preLocation, location);
        if (path != null && path.size() > 1) {
            animateLocation(path);
            //App.activity.showToastOnUiThread("0 start");
            for (LocationListener hl : snapLocationListeners)
                //It's important to send the second element, so this is a real Point on path
                //First is a calculated path dependent on position
                hl.onLocationChanged(path.get(1));
        } else {
            if (anim != null) {
                anim.cancel();
            }
            onVirtualLocationChanged(location);
            //Set real location to get a new Route
            for (LocationListener hl : snapLocationListeners)
                hl.onLocationChanged(location);
        }

        preLocation = location;
    }

    private void animateLocation(final ArrayList<Location> path) {
        if (path.size() < 2) return;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        Location first = path.get(0);
        double distance = first.distanceTo(path.get(1));
        long time = (long) ((distance / first.getSpeed()) * 1000);
        anim.setDuration(time);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (float) animation.getAnimatedValue();
                Location l = calculateProgressLocation(path.get(0), path.get(1), progress);
                onVirtualLocationChanged(l);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ArrayList<Location> p = (ArrayList<Location>) path.clone();
                p.remove(0);
                animateLocation(p);
                //App.activity.showToastOnUiThread("Virtual location end");
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                //Remove onAnimationEndListeners to avoid calling a new animation
                animation.removeAllListeners();
            }
        });
        anim.setInterpolator(new LinearInterpolator());
        anim.start();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Notify everybody that may be interested.
        for (LocationListener hl : virtualLocationListeners)
            hl.onProviderDisabled(provider);
        for (LocationListener hl : snapLocationListeners)
            hl.onProviderDisabled(provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Notify everybody that may be interested.
        for (LocationListener hl : virtualLocationListeners)
            hl.onProviderEnabled(provider);
        for (LocationListener hl : snapLocationListeners)
            hl.onProviderEnabled(provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Notify everybody that may be interested.
        for (LocationListener hl : virtualLocationListeners)
            hl.onStatusChanged(provider, status, extras);
        for (LocationListener hl : snapLocationListeners)
            hl.onStatusChanged(provider, status, extras);
    }

    public void setCenterOnFirstFix() {
        mSetCenter = true;
    }

    public void pause() {
        if (mMode != Mode.OFF) {
            log.debug("pause location listener");
        }
    }

    public void resume() throws SecurityException {
        if (mMode != Mode.OFF) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            String bestProvider = mLocationManager.getBestProvider(criteria, true);
            mLocationManager.requestLocationUpdates(bestProvider, GPS_MINIMUM_TIME_ELAPSE,
                    GPS_MINIMUM_DISTANCE, this);
        }
    }

    //Location Listeners, which listen this Location-Handler
    private final static Logger log = LoggerFactory.getLogger(LocationHandler.class);
    private Collection<LocationListener> virtualLocationListeners = new HashSet<LocationListener>();

    public void addVirtualLocationListener(LocationListener toAdd) {
        virtualLocationListeners.add(toAdd);
    }

    private Collection<LocationListener> snapLocationListeners = new HashSet<LocationListener>();

    public void addSnapLocationListener(LocationListener toAdd) {
        snapLocationListeners.add(toAdd);
    }

}
