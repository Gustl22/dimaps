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
import android.os.Bundle;

import com.vividsolutions.jts.math.Vector2D;

import org.oscim.app.App;
import org.oscim.app.R;
import org.oscim.app.TileMap;
import org.oscim.core.MapPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;

import static org.oscim.app.App.map;

public class LocationHandler implements LocationListener {

    public enum Mode {
        OFF,
        SHOW,
        SNAP,
    }

    private final static int DIALOG_LOCATION_PROVIDER_DISABLED = 2;
    private final static int SHOW_LOCATION_ZOOM = 14;

    private final LocationManager mLocationManager;
    private final LocationLayerImpl mLocationLayer;

    private Mode mMode = Mode.OFF;

    private boolean mSetCenter;
    private MapPosition mMapPosition;

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

            if (mMode == Mode.SNAP)
                map.getEventLayer().enableMove(true);
        }

        if (mMode == Mode.OFF) {
            if (!enableShowMyLocation())
                return false;
        }

        if (mode == Mode.SNAP) {
            map.getEventLayer().enableMove(false);
            gotoLastKnownPosition();
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
    private boolean enableShowMyLocation() {

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String bestProvider = mLocationManager.getBestProvider(criteria, true);

        if (bestProvider == null) {
            App.activity.showDialog(DIALOG_LOCATION_PROVIDER_DISABLED);
            return false;
        }

        mLocationManager.requestLocationUpdates(bestProvider, 5000, 5, this);

        Location location = gotoLastKnownPosition();
        if (location == null)
            return false;

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

    public Location gotoLastKnownPosition() {
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

        if (mMapPosition.zoomLevel < SHOW_LOCATION_ZOOM)
            mMapPosition.setZoomLevel(SHOW_LOCATION_ZOOM);

        //Set Location to bottom for navigation
        double radian = Math.toRadians(location.getBearing());
        double y = Math.sin(radian);
        double x = Math.cos(radian);
        Vector2D vector = new Vector2D(x, y);
        vector = vector.divide(vector.length()); //Unit vector
        double distance = App.map.getMapPosition().getTilt()/100;
        vector = vector.multiply(0.001*distance); //Distance Value (dependent on the map tilt)

        //Set Map position
        mMapPosition.setPosition(location.getLatitude()+vector.getX(), location.getLongitude()+vector.getY());
        App.map.setMapPosition(mMapPosition);

        return location;
    }

    /***
     * LocationListener
     ***/
    ValueAnimator anim;
    Location preLocation;

    public void onVirtualLocationChanged(Location location){
        //Inform Compass about big location changes
        for (LocationListener hl : listeners)
            hl.onLocationChanged(location);

        if (mMode == Mode.OFF)
            return;

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        log.debug("update location " + lat + ":" + lon);

        if (mSetCenter || mMode == Mode.SNAP) {
            mSetCenter = false;

            map.getMapPosition(mMapPosition);
            mMapPosition.setPosition(lat, lon);
            map.setMapPosition(mMapPosition);
        }

        mLocationLayer.setPosition(lat, lon, location.getAccuracy());
    }

    public Location calculateNextLocation(Location preLocation, Location currentLocation){
        Location location = new Location(currentLocation);
        double diffLat = currentLocation.getLatitude()-preLocation.getLatitude();
        double diffLon = currentLocation.getLongitude()-preLocation.getLongitude();
        location.setLatitude(location.getLatitude()+diffLat);
        location.setLongitude(location.getLongitude()+diffLon);
        return location;
    }

    public Location calculateProgressLocation(Location startLocation, Location endLocation, float progress){
        Location location = new Location(startLocation);
        double diffLat = endLocation.getLatitude()-startLocation.getLatitude();
        double diffLon = endLocation.getLongitude()-startLocation.getLongitude();
        float diffBearing = endLocation.getBearing()-startLocation.getBearing();
        long diffTime = endLocation.getTime()-startLocation.getTime();
        float diffSpeed = endLocation.getSpeed()-startLocation.getSpeed();
        if(diffLat != 0) location.setLatitude(location.getLatitude() + diffLat*progress);
        if(diffLon != 0)location.setLongitude(location.getLongitude() + diffLon*progress);
        if(diffBearing != 0f)location.setBearing(location.getBearing() + diffBearing*progress);
        if(diffTime != 0) location.setTime(location.getTime() + (long)(diffTime*progress));
        if(diffSpeed != 0f)location.setSpeed(location.getSpeed() + diffSpeed*progress);
        return location;
    }

    @Override
    public void onLocationChanged(final Location location) {
        if(preLocation == null){
            onVirtualLocationChanged(location);
            preLocation = location;
            return;
        }
        //Animate big rotation steps
        final Location startLocation = location;
        final Location endLocation = calculateNextLocation(preLocation, location);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(5000);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = (float) animation.getAnimatedValue();
                Location l = calculateProgressLocation(startLocation, endLocation, progress);
                onVirtualLocationChanged(l);
            }
        });
        anim.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationEnd(Animator animation)
            {

            }
        });
        anim.start();

        preLocation = location;
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Notify everybody that may be interested.
        for (LocationListener hl : listeners)
            hl.onProviderDisabled(provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Notify everybody that may be interested.
        for (LocationListener hl : listeners)
            hl.onProviderEnabled(provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Notify everybody that may be interested.
        for (LocationListener hl : listeners)
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

    public void resume() {
        if (mMode != Mode.OFF) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            String bestProvider = mLocationManager.getBestProvider(criteria, true);
            mLocationManager.requestLocationUpdates(bestProvider, 5000, 5, this);
        }
    }

    //Location Listeners, which listen this Location-Handler
    private final static Logger log = LoggerFactory.getLogger(LocationHandler.class);
    private Collection<LocationListener> listeners = new HashSet<LocationListener>();

    public void addListener(LocationListener toAdd) {
        listeners.add(toAdd);
    }
}
