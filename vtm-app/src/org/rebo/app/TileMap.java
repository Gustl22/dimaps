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
package org.rebo.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
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
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.oscim.android.MapView;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeoPoint;
import org.oscim.core.Tile;
import org.oscim.renderer.MapRenderer;
import org.oscim.utils.Parameters;
import org.osmdroid.location.POI;
import org.osmdroid.overlays.ExtendedMarkerItem;
import org.osmdroid.overlays.MapEventsReceiver;
import org.rebo.app.debug.RemoteDebugger;
import org.rebo.app.download.MapDownloadActivity;
import org.rebo.app.graphhopper.CrossMapCalculatorListener;
import org.rebo.app.location.Compass;
import org.rebo.app.location.LocationDialog;
import org.rebo.app.location.LocationHandler;
import org.rebo.app.navigation.Navigation;
import org.rebo.app.permission.Permission;
import org.rebo.app.poi.PoiManager;
import org.rebo.app.preferences.EditPreferences;
import org.rebo.app.route.RouteActivity;
import org.rebo.app.route.RouteSearch;
import org.rebo.app.poi.PoiFavoritesActivity;
import org.rebo.app.poi.PoiSearchActivity;
import org.rebo.app.utils.ColorUtils;
import org.rebo.app.utils.CustomAnimationUtils;
import org.rebo.overlay.DistanceTouchOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.rebo.app.App.activity;
import static org.rebo.app.App.routeSearch;
import static org.rebo.app.location.LocationHandler.Mode.SNAP;


public class TileMap extends MapActivity implements MapEventsReceiver,
        NavigationView.OnNavigationItemSelectedListener, CrossMapCalculatorListener,
        RouteSearch.RouteSearchListener {
    static final Logger log = LoggerFactory.getLogger(TileMap.class);

    private static final int DIALOG_ENTER_COORDINATES = 0;
    private static final int DIALOG_LOCATION_PROVIDER_DISABLED = 2;

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
    private FrameLayout mCompassFrame;
    //SearchBar
    private EditText mSearchBar;

    private MapLayers mMapLayers;

    public MapLayers getMapLayers() {
        return mMapLayers;
    }

    private DistanceTouchOverlay mDistanceTouch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Debug
        RemoteDebugger.setExceptionHandler(this);

        // Set VTM preferences
        Parameters.ANIMATOR2 = true;
        Parameters.CUSTOM_COORD_SCALE = true;
        MapRenderer.COORD_SCALE = 2.0f;

        // Init view
        setContentView(R.layout.activity_tilemap_nav);
        App.view = (MapView) findViewById(R.id.mapView);
        registerMapView(App.view);

        App.map = mMap;
        activity = this;

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        mLocation.addVirtualLocationListener(mCompass);

        App.poiSearch = new POISearch(); // TODO remove

        // Init POIs, must be set after MapLayers
        App.poiManager = new PoiManager();
        App.poiManager.loadPreferences(this);

        App.routeSearch = new RouteSearch();
        routeSearch.addRouteSearchListener(this);

        registerForContextMenu(App.view);
        //Navigationview
        mToolbar = (LinearLayout) findViewById(R.id.toolbar);
        mSearchBar = (EditText) findViewById(R.id.search_bar);
        mSearchBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(activity, PoiSearchActivity.class));
            }
        });
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        mCompassFrame = (FrameLayout) activity.findViewById(R.id.compass_wrapper);
        mCompassFab = (FloatingActionButton) activity.findViewById(R.id.compass);
        mCompassFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCompass(null);
            }
        });
        mCompassFab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleCompass(Compass.Mode.C3D);
                return true;
            }
        });
        mLocationFab = (FloatingActionButton) activity.findViewById(R.id.location);
        mLocationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleLocation(null);
            }
        });
        mLocationFab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Navigation nav;
                if (routeSearch != null && (nav = routeSearch.getNavigation()) != null) {
                    BoundingBox bbox = nav.getRouteBounds();
                    if (bbox != null) {
                        bbox = bbox.extendMargin(2f);
                        mMap.animator().animateTo(bbox);
                        mMap.updateMap(true);
                    }
                }
                //toggleLocation(LocationHandler.Mode.SNAP);
                return true;
            }
        });

        handleIntent(getIntent(), true);
        // Here, this.Activity is the current activity

        Permission.requestLocationPermission();

        delayBlankMode();
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
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
                startActivity(new Intent(this, RouteActivity.class));
                break;
            case R.id.route_instructions:
//                TODO fetch instructions
                break;
            case R.id.route_settings:
//                TODO preferences for current route
                break;
            case R.id.my_places:
                startActivity(new Intent(this, PoiFavoritesActivity.class));
                break;
            case R.id.tools:
//                TODO create tools
                break;
            case R.id.maps_download:
                startActivity(new Intent(this, MapDownloadActivity.class));
                break;
            case R.id.settings:
                startActivity(new Intent(this, EditPreferences.class));
                break;
            case R.id.legend:
//                TODO write legend for map
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
                .setChecked(mLocation.getMode() == SNAP);

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

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        routeSearch.storeRoutePoints();
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
    static Toast toast;

    public void showToastOnUiThread(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(toast != null)
                    toast.cancel();
                if (text.length() < 20) {
                    toast = Toast.makeText(TileMap.this, text, Toast.LENGTH_SHORT);
                } else {
                    toast = Toast.makeText(TileMap.this, text, Toast.LENGTH_LONG);
                }
                toast.show();
            }
        });
    }

    public void toggleCompass(Compass.Mode mode) {
        if (mode == null) {
            switch (mCompass.getMode()) {
                case OFF:
                    mode = Compass.Mode.C2D;
                    break;
                default:
                    mode = Compass.Mode.OFF;
                    break;
            }
        }
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

        mCompass.setMode(mode);
        switch (mode) {
            case NAV:
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.adjustAlpha(getResources().getColor(R.color.colorAccent), 0.4f)));
                break;
            case C2D:
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.adjustAlpha(getResources().getColor(R.color.colorAccent), 0.4f)));
                //App.activity.showToastOnUiThread("Compass 2D");
                break;
            case C3D:
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.adjustAlpha(getResources().getColor(R.color.colorSecondAccent), 0.4f)));
                //App.activity.showToastOnUiThread("Compass 3D");
                break;
            case OFF:
                mCompassFab.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.adjustAlpha(getResources().getColor(R.color.white), 0.4f)));
                mCompass.setRotation(0);
                mCompass.setTilt(0);
                //App.activity.showToastOnUiThread("Manual");
                break;
            default:
                break;
        }

        App.map.updateMap(true);
    }

    public void toggleLocation(LocationHandler.Mode setMode) {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (setMode == null) {
            LocationHandler.Mode mode = mLocation.getMode();


            switch (mode) {
                    case OFF:
                        if (routeSearch != null && routeSearch.getDestinationPoint() != null
                                && mode != LocationHandler.Mode.NAV) {
                            setMode = LocationHandler.Mode.NAV;
                        } else {
                            setMode = SNAP;
                        }
                        break;
                case NAV:
                    case SNAP:
                        setMode = LocationHandler.Mode.OFF;
                        break;
                    default:
                        setMode = SNAP;
                        break;
                }
        }
        //Ask if GPS is activated?
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
            if (setMode != LocationHandler.Mode.OFF) {
                mLocation.setMode(LocationHandler.Mode.OFF);
                mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.disabledBackgroundLight));
                mLocationFab.setImageResource(R.drawable.ic_location_disabled_white_24dp);
            }
        } else {
            boolean success = false;
            switch (setMode) {
                case SHOW:
                    if(success = mLocation.setMode(LocationHandler.Mode.SHOW)){
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.colorSecondAccent));
                        mLocationFab.setImageResource(R.drawable.ic_find_location_white_24dp);
                    }
                    break;
                case SNAP:
                    if (success = mLocation.setMode(SNAP)) {
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.colorAccent));
                        mLocationFab.setImageResource(R.drawable.ic_my_location_white_24dp);
                    }
                    break;
                case OFF:
                    if(success = mLocation.setMode(LocationHandler.Mode.OFF)){
                        mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.disabledBackgroundLight));
                        mLocationFab.setImageResource(R.drawable.ic_location_disabled_white_24dp);
                    }
                    break;
                case NAV:
                    if (routeSearch != null && routeSearch.getDestinationPoint() != null) {
                        if (success = mLocation.setMode(LocationHandler.Mode.NAV)) {
                            mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.colorAccent));
                            mLocationFab.setImageResource(R.drawable.ic_navigation_white_24dp);
                        }
                        toggleCompass(Compass.Mode.NAV);
                        mCompass.setTilt(80);
                    }
                    break;
                default:
                    success = true;
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
            checkBlankMode();
        } else {
            uncheckBlankMode();
        }
        return true;
    }

    private void checkBlankMode(){
        if(!isBlankMode) {
            CustomAnimationUtils.SlideUp(mToolbar, this);
            CustomAnimationUtils.SlideRight(mLocationFab, this);
            CustomAnimationUtils.SlideRight(mCompassFrame, this);
            isBlankMode = true;
        }
    }

    private void uncheckBlankMode(){
        if(isBlankMode) {
            CustomAnimationUtils.SlideYBack(mToolbar, this);
            CustomAnimationUtils.SlideXBack(mLocationFab, this);
            CustomAnimationUtils.SlideXBack(mCompassFrame, this);
            isBlankMode = false;
            delayBlankMode();
        }
    }

    final Handler handler = new Handler();
    private void delayBlankMode(){
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkBlankMode();
            }
        }, 45000);
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        mLongPressGeoPoint = p;
        openContextMenu(App.view);
        return true;
    }

    @Override
    public boolean longPressHelper(final GeoPoint p1, final GeoPoint p2) {
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(50, 100));
        } else {
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
        }
//        showToastOnUiThread("Distance Touch!");
        App.routeSearch.showRoute( p1, p2);
        return true;
    }

    @Override
    public boolean tabSlideHelper(GeoPoint p1, GeoPoint p2) {
        if (mLocation.getMode() == SNAP
                || mLocation.getMode() == LocationHandler.Mode.NAV)
            toggleLocation(LocationHandler.Mode.SHOW);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permission.ACCESS_FINE_LOCATION: {
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
            mMap.viewport().setMaxTilt(79);
            float tilt = mCompass.getTilt() + 15;
            mCompass.setTilt(tilt);
            //getLayoutInflater().inflate(mCompassFab, );
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            //Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
            mMap.viewport().setMaxTilt(70);
            float tilt = mCompass.getTilt() - 15;
            mCompass.setTilt(tilt);
        }
    }

    ProgressDialog progressDialog;
    @Override
    public void onCrossMapCalculatorUpdate(final String status, final int progress, final int style) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progress == 0) {
                    if(progressDialog == null){
                        progressDialog = new ProgressDialog(activity);
                        progressDialog.setProgressStyle(style);
                        progressDialog.setMessage(status);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    }
                    progressDialog.setMessage(status);
                } else if (progress == 100) {
                    if (progressDialog != null)
                        progressDialog.dismiss();
                    progressDialog = null;
                }
                if(progressDialog != null)
                    progressDialog.setProgress(progress);
            }
        });
    }

    @Override
    public void onWaypointSet(RouteSearch.MarkerType type, ExtendedMarkerItem item) {
        if (type.equals(RouteSearch.MarkerType.Destination)) {
            mLocationFab.setBackgroundTintList(getBaseContext().getResources().getColorStateList(R.color.white));
            mLocationFab.setImageResource(R.drawable.ic_navigation_black_24dp);
        }
    }

    @Override
    public void onWaypointRemoved(RouteSearch.MarkerType type, ExtendedMarkerItem item) {
        if (type.equals(RouteSearch.MarkerType.Destination)) {
            toggleLocation(SNAP);
        }
    }
}
