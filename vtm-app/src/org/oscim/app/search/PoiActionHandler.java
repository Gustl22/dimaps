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
    private Activity mActivity;
    private PointOfInterest mPoi;
    private File mPoiFile;
    private GHPoint mPoiLocation;
    private RouteSearch mRouteSearch = App.routeSearch;
    private List<File> mGhFiles = RouteSearch.getGraphHopperFiles();
    private View mvDepartureButton;
    private View mvDestinationButton;
    private View mvShowMapButton;
    private View mvFavoriteButton;
    private View mvShareButton;
    private View mvDeleteFavorButton;

    public PoiActionHandler(Activity activity) {
        this.mActivity = activity;
    }


    public void addDestination() {
        if (mPoi == null || mGhFiles == null) return;
        GHPointArea area = new GHPointArea(mPoiLocation, mGhFiles);
        if (mRouteSearch.getDestinationPoint() == null) {
            mRouteSearch.setDestinationPoint(area);
        } else {
            mRouteSearch.addViaPoint(area);
        }
        App.activity.showToastOnUiThread("Waypoint added");
    }

    public void setDeparture() {
        if (mPoi == null || mGhFiles == null) return;
        mRouteSearch.setStartPoint(new GHPointArea(mPoiLocation, mGhFiles));
        App.activity.showToastOnUiThread("Start set");
    }

    public void showOnMap() {
        if (mPoi == null) return;
        mRouteSearch.addNonRoutePoint(new GHPointArea(mPoiLocation, mGhFiles));
        mActivity.finish();
    }

    public void markAsFavorite() {
        if (mPoi == null || mPoiFile == null || !mPoiFile.exists()) return;
        PoiFavoritesHandler favorHandler = PoiFavoritesHandler.getInstance();
        favorHandler.addFavorite(mPoi, mPoiFile.getParentFile());
        mActivity.startActivity(new Intent(App.activity, PoiFavoritesActivity.class));
        mActivity.finish();
    }

    public void removeFavorite() {
        if (mPoi == null || mPoiFile == null || !mPoiFile.exists()) return;
        PoiFavoritesHandler favorHandler = PoiFavoritesHandler.getInstance();
        favorHandler.removeFavorite(mPoi, mPoiFile.getParentFile());
        mActivity.recreate();
    }

    //Getter and Setter
    public View getDepartureButton() {
        return mvDepartureButton;
    }

    public void setDepartureButton(View button) {
        if (button == null) return;
        this.mvDepartureButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDeparture();
            }
        });
    }

    public View getDestinationButton() {
        return mvDestinationButton;
    }

    public void setDestinationButton(View button) {
        if (button == null) return;
        this.mvDestinationButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDestination();
            }
        });
    }

    public View getShowMapButton() {
        return mvShowMapButton;
    }

    public void setShowMapButton(View button) {
        if (button == null) return;
        this.mvShowMapButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOnMap();
            }
        });
    }

    public View getFavoriteButton() {
        return mvFavoriteButton;
    }

    public void setFavoriteButton(View button) {
        if (button == null) return;
        this.mvFavoriteButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAsFavorite();
            }
        });
    }

    public View getShareButton() {
        return mvShareButton;
    }

    public void setShareButton(View button) {
        if (button == null) return;
        this.mvShareButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRouteSearch.shareLocation(mPoiLocation);
            }
        });
    }

    public View getDeleteFavorButton() {
        return mvDeleteFavorButton;
    }

    public void setDeleteFavorButton(View button) {
        if (button == null) return;
        this.mvDeleteFavorButton = button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeFavorite();
            }
        });
    }

    public PointOfInterest getPoi() {
        return mPoi;
    }

    public void setPoi(PointOfInterest poi, File poiFile) {
        if (poi == null || poiFile == null || !poiFile.exists()) return;
        mPoiLocation = new GHPoint(poi.getLatitude(), poi.getLongitude());
        this.mPoi = poi;
        this.mPoiFile = poiFile;
    }


}
