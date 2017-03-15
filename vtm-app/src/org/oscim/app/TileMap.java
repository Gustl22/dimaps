/* Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2012 Hannes Janetzek
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
package org.oscim.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.oscim.android.MapView;
import org.oscim.app.download.MapDownloader;
import org.oscim.app.filepicker.Utils;
import org.oscim.app.location.Compass;
import org.oscim.app.location.LocationDialog;
import org.oscim.app.location.LocationHandler;
import org.oscim.core.GeoPoint;
import org.oscim.core.Tile;
import org.oscim.overlay.DistanceTouchOverlay;
import org.osmdroid.location.POI;
import org.osmdroid.overlays.MapEventsReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TileMap extends MapActivity implements MapEventsReceiver, NavigationView.OnNavigationItemSelectedListener {
    final static Logger log = LoggerFactory.getLogger(TileMap.class);

    private static final int DIALOG_ENTER_COORDINATES = 0;
    private static final int DIALOG_LOCATION_PROVIDER_DISABLED = 2;
    private static final int ACCESS_FINE_LOCATION = 1;

    //private static final int SELECT_RENDER_THEME_FILE = 1;
    protected static final int POIS_REQUEST = 2;

    private LocationHandler mLocation;
    private Compass mCompass;

    private Menu mMenu = null;


    //Navigationview
    private NavigationView mNavigationView;
    private DrawerLayout mDrawerLayout;
    private LinearLayout mToolbar;
    private FloatingActionButton mLocationFab;
    private FloatingActionButton mCompassFab;

    private MapLayers mMapLayers;

    public MapLayers getMapLayers() {
        return mMapLayers;
    }

    private DistanceTouchOverlay mDistanceTouch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Tile.SIZE = Tile.calculateTileSize(getResources().getDisplayMetrics().scaledDensity);
        setContentView(R.layout.activity_tilemap_nav);
        App.view = (MapView) findViewById(R.id.mapView);
        registerMapView(App.view);

        App.map = mMap;
        App.activity = this;

        mMapLayers = new MapLayers();
        mMapLayers.setBaseMap(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (!prefs.contains("distanceTouch"))
            prefs.edit().putBoolean("distanceTouch", true).apply();

        if (prefs.getBoolean("distanceTouch", true)) {
            mDistanceTouch = new DistanceTouchOverlay(mMap, this);
            mMap.layers().add(mDistanceTouch);
        }

        mCompass = new Compass(this, mMap);
        mMap.layers().add(mCompass);

        mLocation = new LocationHandler(this, mCompass);

        App.poiSearch = new POISearch();
        App.routeSearch = new RouteSearch();

        registerForContextMenu(App.view);
        //Navigationview
        mToolbar = (LinearLayout) findViewById(R.id.toolbar);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        mCompassFab = (FloatingActionButton) App.activity.findViewById(R.id.compass);
        mCompassFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCompass();
            }
        });
        mLocationFab = (FloatingActionButton) App.activity.findViewById(R.id.location);
        mLocationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                toggleLoction();
            }
        });

        //Handle GPS Permissions
        handleIntent(getIntent(), true);
        // Here, thisActivity is the current activity
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        ACCESS_FINE_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public Compass getCompass() {
        return mCompass;
    }

    public LocationHandler getLocationHandler() {
        return mLocation;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, false);
    }

    private void handleIntent(Intent intent, boolean start) {
        if (intent == null)
            return;

        Uri uri = intent.getData();
        if (uri != null) {
            String scheme = uri.getSchemeSpecificPart();
            log.debug("got intent: " + (scheme == null ? "" : scheme));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        mMenu = menu;
        toggleMenuCheck();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        int position = 0;
        switch (id) {
            case R.id.waypoints:
//                mTitle = getString(R.string.title_home);
//                position = 0;
                break;
            case R.id.route_instructions:
//                mTitle = getString(R.string.title_bookmarks);
//                position = 1;
                break;
            case R.id.route_settings:
//                mTitle = getString(R.string.title_favorite);
//                position = 2;
                break;
            case R.id.my_places:
//                mTitle = getString(R.string.title_payment);
//                position = 3;
                break;
            case R.id.tools:
//                mTitle = getString(R.string.title_settings);
//                position = 4;
                break;
            case R.id.maps_download:
                startActivity(new Intent(this, MapDownloader.class));
                break;
            case R.id.settings:
//                mTitle = getString(R.string.title_settings);
//                position = 4;
                break;
            case R.id.legend:
//                mTitle = getString(R.string.title_settings);
//                position = 4;
                break;
            case R.id.about:
                startActivity(new Intent(this, InfoView.class));
                break;
        }
//        if (getSupportActionBar() != null)
//            getSupportActionBar().setTitle(mTitle);
        mDrawerLayout.closeDrawer(GravityCompat.START);

//        FragmentManager fragmentManager = getSupportFragmentManager();
//        fragmentManager.beginTransaction()
//                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
//                .commit();

        return true;
    }

//    @SuppressWarnings("deprecation")
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//
//        switch (item.getItemId()) {
//            case R.id.menu_info_about:
//                startActivity(new Intent(this, InfoView.class));
//                break;
//
//            case R.id.menu_position:
//                break;
//
//            case R.id.menu_poi_nearby:
//                Intent intent = new Intent(this, POIActivity.class);
//                startActivityForResult(intent, TileMap.POIS_REQUEST);
//                break;
//
//            case R.id.menu_compass_2d:
//                if (!item.isChecked()) {
//                    // FIXME
//                    //mMapView.getMapViewPosition().setTilt(0);
//                    mCompass.setMode(Compass.Mode.C2D);
//                } else {
//                    mCompass.setMode(Compass.Mode.OFF);
//                }
//                break;
//
//            case R.id.menu_compass_3d:
//                if (!item.isChecked()) {
//                    mCompass.setMode(Compass.Mode.C3D);
//                } else {
//                    mCompass.setMode(Compass.Mode.OFF);
//                }
//                break;
//
//            case R.id.menu_position_my_location_enable:
//                if (!item.isChecked()) {
//                    mLocation.setMode(LocationHandler.Mode.SHOW);
//                    mLocation.setCenterOnFirstFix();
//                } else {
//                    mLocation.setMode(LocationHandler.Mode.OFF);
//                }
//                break;
//
//            case R.id.menu_position_follow_location:
//                if (!item.isChecked()) {
//                    mLocation.setMode(LocationHandler.Mode.SNAP);
//                } else {
//                    mLocation.setMode(LocationHandler.Mode.OFF);
//                }
//                break;
//
//            case R.id.menu_layer_openstreetmap:
//            case R.id.menu_layer_naturalearth:
//                int bgId = item.getItemId();
//                // toggle if already enabled
//                if (bgId == mMapLayers.getBackgroundId())
//                    bgId = -1;
//
//                mMapLayers.setBackgroundMap(bgId);
//                mMap.updateMap(true);
//                break;
//
//            case R.id.menu_layer_grid:
//                mMapLayers.enableGridOverlay(this, !mMapLayers.isGridEnabled());
//                mMap.updateMap(true);
//                break;
//
//            case R.id.menu_position_enter_coordinates:
//                showDialog(DIALOG_ENTER_COORDINATES);
//                break;
//
//            //case R.id.menu_position_map_center:
//            //    MapPosition mapCenter = mBaseLayer.getMapFileCenter();
//            //    if (mapCenter != null)
//            //        mMap.setCenter(mapCenter.getGeoPoint());
//            //    break;
//
//            case R.id.menu_preferences:
//                startActivity(new Intent(this, EditPreferences.class));
//                overridePendingTransition(R.anim.slide_right, R.anim.slide_left2);
//                break;
//
//            default:
//                return false;
//        }
//
//        toggleMenuCheck();
//
//        return true;
//    }

    private void toggleMenuCheck() {

        mMenu.findItem(R.id.menu_compass_2d)
                .setChecked(mCompass.getMode() == Compass.Mode.C2D);
        mMenu.findItem(R.id.menu_compass_3d)
                .setChecked(mCompass.getMode() == Compass.Mode.C3D);

        mMenu.findItem(R.id.menu_position_my_location_enable)
                .setChecked(mLocation.getMode() == LocationHandler.Mode.SHOW);
        mMenu.findItem(R.id.menu_position_follow_location)
                .setChecked(mLocation.getMode() == LocationHandler.Mode.SNAP);

        int bgId = mMapLayers.getBackgroundId();
        mMenu.findItem(R.id.menu_layer_naturalearth)
                .setChecked(bgId == R.id.menu_layer_naturalearth);

        mMenu.findItem(R.id.menu_layer_openstreetmap)
                .setChecked(bgId == R.id.menu_layer_openstreetmap);

        mMenu.findItem(R.id.menu_layer_grid)
                .setChecked(mMapLayers.isGridEnabled());
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (!isPreHoneyComb()) {
            menu.clear();
            onCreateOptionsMenu(menu);
        }

        menu.findItem(R.id.menu_position_map_center).setVisible(false);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case POIS_REQUEST:
                log.debug("result: POIS_REQUEST");
                if (resultCode == RESULT_OK) {
                    int id = intent.getIntExtra("ID", 0);
                    log.debug("result: POIS_REQUEST: " + id);

                    App.poiSearch.poiMarkers.showBubbleOnItem(id);
                    POI poi = App.poiSearch.getPOIs().get(id);

                    if (poi.bbox != null)
                        mMap.animator().animateTo(poi.bbox);
                    else
                        mMap.animator().animateTo(poi.location);
                }
                break;
            default:
                break;
        }
    }

    static boolean isPreHoneyComb() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (id == DIALOG_ENTER_COORDINATES) {
            if (mLocationDialog == null)
                mLocationDialog = new LocationDialog();

            return mLocationDialog.createDialog(this);

        } else if (id == DIALOG_LOCATION_PROVIDER_DISABLED) {
            builder.setIcon(android.R.drawable.ic_menu_info_details);
            builder.setTitle(R.string.error);
            builder.setMessage(R.string.no_location_provider_available);
            builder.setPositiveButton(R.string.ok, null);
            return builder.create();
        } else {
            // no dialog will be created
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCompass.pause();
        mLocation.pause();
    }

    LocationDialog mLocationDialog;

    @SuppressWarnings("deprecation")
    @Override
    protected void onPrepareDialog(int id, final Dialog dialog) {
        if (id == DIALOG_ENTER_COORDINATES) {

            mLocationDialog.prepareDialog(mMap, dialog);

        } else {
            super.onPrepareDialog(id, dialog);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCompass.resume();
        mLocation.resume();

        mMapLayers.setPreferences(this);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getBoolean("fullscreen", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }

        //App.lockOrientation(this);

        boolean distanceTouch = preferences.getBoolean("distanceTouch", true);
        if (distanceTouch) {
            if (mDistanceTouch == null) {
                mDistanceTouch = new DistanceTouchOverlay(mMap, this);
                mMap.layers().add(mDistanceTouch);
            }
        } else {
            mMap.layers().remove(mDistanceTouch);
            mDistanceTouch = null;
        }

        mMap.updateMap(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Uses the UI thread to display the given text message as toast
     * notification.
     *
     * @param text the text message to display
     */
    public void showToastOnUiThread(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(TileMap.this, text, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    public void toggleCompass() {

        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

        switch(mCompass.getMode()){
            case OFF:
                mCompass.setMode(Compass.Mode.C2D);
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(Utils.adjustAlpha(getResources().getColor(R.color.colorAccent), 0.4f)));
                //App.activity.showToastOnUiThread("Compass 2D");
                break;
            case C2D:
                mCompass.setMode(Compass.Mode.C3D);
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(Utils.adjustAlpha(getResources().getColor(R.color.colorSecondAccent), 0.4f)));
                //App.activity.showToastOnUiThread("Compass 3D");
                break;
            case C3D:
                mCompass.setMode(Compass.Mode.OFF);
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(Utils.adjustAlpha(getResources().getColor(R.color.white), 0.4f)));
                mCompass.setRotation(0);
                mCompass.setTilt(0);
                //App.activity.showToastOnUiThread("Manual");
                break;
            default:
                break;
        }

        App.map.updateMap(true);
    }

    private void toggleLoction() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LocationHandler.Mode mode = mLocation.getMode();
        //Ask if GPS is activated?
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
            if(mode != LocationHandler.Mode.OFF){
                mLocation.setMode(LocationHandler.Mode.OFF);
                mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.disabledBackgroundLight));
                mLocationFab.setImageResource(R.drawable.ic_location_disabled_white_24dp);
            }
        } else {
            boolean success = true;
            switch (mode){
                case OFF:
                    if(success = mLocation.setMode(LocationHandler.Mode.SHOW)){
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.colorSecondAccent));
                        mLocationFab.setImageResource(R.drawable.ic_find_location_white_24dp);
                    }
                    break;
                case SHOW:
                    if(success = mLocation.setMode(LocationHandler.Mode.SNAP)){
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.colorAccent));
                        mLocationFab.setImageResource(R.drawable.ic_my_location_white_24dp);
                    }
                    break;
                case SNAP:
                    if(success = mLocation.setMode(LocationHandler.Mode.OFF)){
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.disabledBackgroundLight));
                        mLocationFab.setImageResource(R.drawable.ic_location_disabled_white_24dp);
                    }
                    break;
                default:
                    break;
            }
            if(!success){
                mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.disabledBackgroundLight));
                mLocationFab.setImageResource(R.drawable.ic_location_searching_white_24dp);
            }
        }
    }

    public void toggleNavigationView(View v){
        mDrawerLayout.openDrawer(GravityCompat.START);

    }

    /**
     * Context Menu when clicking on the {@link Map}
     */
    private GeoPoint mLongPressGeoPoint;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map_menu, menu);

        if (App.poiSearch.getPOIs().isEmpty())
            menu.removeItem(R.id.menu_poi_clear);

        if (App.routeSearch.isEmpty())
            menu.removeItem(R.id.menu_route_clear);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (App.poiSearch.onContextItemSelected(item, mLongPressGeoPoint))
            return true;

        if (App.routeSearch.onContextItemSelected(item, mLongPressGeoPoint))
            return true;

        return super.onContextItemSelected(item);
    }

    /**
     * MapEventsReceiver implementation
     */

    private boolean isBlankMode = false;
    @Override
    public boolean singleTapUpHelper(GeoPoint p) {
        if(!isBlankMode){
            CustomAnimationUtils.SlideUp(mToolbar, this);
            CustomAnimationUtils.SlideRight(mLocationFab, this);
            CustomAnimationUtils.SlideRight(mCompassFab, this);
            isBlankMode = true;
        } else {
//            mToolbar.setVisibility(View.VISIBLE);
            CustomAnimationUtils.SlideUpBack(mToolbar, this);
            CustomAnimationUtils.SlideRightBack(mLocationFab, this);
            CustomAnimationUtils.SlideRightBack(mCompassFab, this);
            isBlankMode = false;
            //mToolbar.clearAnimation();
        }

        return true;
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        mLongPressGeoPoint = p;
        openContextMenu(App.view);
        return true;
    }

    @Override
    public boolean longPressHelper(final GeoPoint p1, final GeoPoint p2) {
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
        showToastOnUiThread("Distance Touch!");
        App.routeSearch.showRoute( p1, p2);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
//        recreate();
     //   setContentView(R.layout.activity_tilemap_nav);
        // Checks the orientation of the screen
//        mCompassFab.invalidate();
//        mLocationFab.invalidate();
//        mToolbar.invalidate();
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
            mMap.viewport().setMaxTilt(80);
            float tilt = mCompass.getTilt() + 15;
            mCompass.setTilt(tilt);
            //getLayoutInflater().inflate(mCompassFab, );
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            //Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
            mMap.viewport().setMaxTilt(65);
            float tilt = mCompass.getTilt() - 15;
            mCompass.setTilt(tilt);
        }
    }
}
