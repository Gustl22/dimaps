package org.oscim.app.search;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.graphhopper.util.shapes.GHPoint;

import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.App;
import org.oscim.app.RouteSearch;
import org.oscim.app.graphhopper.GHPointArea;

import java.io.File;
import java.util.List;

/**
 * Created by gustl on 23.03.17.
 */

public class PoiActionHandler {
    private Activity activity;
    private PointOfInterest poi;
    private File poiFile;
    private GHPoint poiLocation;
    private RouteSearch routeSearch = App.routeSearch;
    private List<File> ghFiles = RouteSearch.getGraphHopperFiles();
    private View startButton;
    private View destinationButton;
    private View showMapButton;
    private View favoriteButton;
    private View shareButton;

    public PoiActionHandler(Activity activity) {
        this.activity = activity;
    }


    public void addDestination() {
        if (poi == null || ghFiles == null) return;
        GHPointArea area = new GHPointArea(poiLocation, ghFiles);
        if (routeSearch.getDestinationPoint() == null) {
            routeSearch.setDestinationPoint(area);
        } else {
            routeSearch.addViaPoint(area);
        }
        App.activity.showToastOnUiThread("Waypoint added");
    }

    public void setStart() {
        if (poi == null || ghFiles == null) return;
        routeSearch.setStartPoint(new GHPointArea(poiLocation, ghFiles));
        App.activity.showToastOnUiThread("Start set");
    }

    public void showOnMap() {
        if (poi == null) return;
        routeSearch.addNonRoutePoint(new GHPointArea(poiLocation, ghFiles));
        activity.finish();
    }

    public void markAsFavorite() {
        if (poi == null || poiFile == null || !poiFile.exists()) return;
        PoiFavoritesHandler favorHandler = PoiFavoritesHandler.getInstance();
        favorHandler.addFavorite(poi, poiFile.getParentFile());
        activity.startActivity(new Intent(App.activity, PoiFavoritesActivity.class));
        activity.finish();
    }

    //Getter and Setter
    public View getStartButton() {
        return startButton;
    }

    public void setStartButton(View button) {
        if (button == null) return;
        this.startButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStart();
            }
        });
    }

    public View getDestinationButton() {
        return destinationButton;
    }

    public void setDestinationButton(View button) {
        if (button == null) return;
        this.destinationButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDestination();
            }
        });
    }

    public View getShowMapButton() {
        return showMapButton;
    }

    public void setShowMapButton(View button) {
        if (button == null) return;
        this.showMapButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOnMap();
            }
        });
    }

    public View getFavoriteButton() {
        return favoriteButton;
    }

    public void setFavoriteButton(View button) {
        if (button == null) return;
        this.favoriteButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAsFavorite();
            }
        });
    }

    public View getShareButton() {
        return shareButton;
    }

    public void setShareButton(View button) {
        if (button == null) return;
        this.shareButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                routeSearch.shareLocation(poiLocation);
            }
        });
    }

    public PointOfInterest getPoi() {
        return poi;
    }

    public void setPoi(PointOfInterest poi, File poiFile) {
        if (poi == null || poiFile == null || !poiFile.exists()) return;
        poiLocation = new GHPoint(poi.getLatitude(), poi.getLongitude());
        this.poi = poi;
        this.poiFile = poiFile;
    }
}
