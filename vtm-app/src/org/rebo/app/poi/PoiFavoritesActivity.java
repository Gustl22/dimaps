package org.rebo.app.poi;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;

import org.mapsforge.poi.storage.PointOfInterest;
import org.rebo.app.App;
import org.rebo.app.R;

import java.util.List;

/**
 * Created by gustl on 24.03.17.
 */

public class PoiFavoritesActivity extends AppCompatActivity {

    private PoiFavoritesHandler favorHandler;

    private PoiDisplayUtils mPoiDisplay;
    private PoiManager mPoiManager = App.poiManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_poi_favorites);

        initPoiDisplay();
        initToolbar();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setTitle("Favorites");
        //toolbar.setSubtitle("OpenDimensionMaps " + version + "!");
    }

    private void initPoiDisplay() {
        favorHandler = new PoiFavoritesHandler(mPoiManager);

        mPoiDisplay = new PoiDisplayUtils(this, mPoiManager, favorHandler);

        List<PointOfInterest> actualPois = favorHandler.getFavorites();

        mPoiDisplay.poiSuggestions.clear();
        mPoiDisplay.poiSuggestions.addAll(actualPois);

        // Must be newly set, otherwise no updates are visible
        mPoiDisplay.searchSpinnerItems =
                PoiDisplayUtils.getSearchItemListFromPoiList(mPoiDisplay.poiSuggestions);

        mPoiDisplay.suggestionsAdapter.clear();
        mPoiDisplay.suggestionsAdapter.addAll(mPoiDisplay.searchSpinnerItems);
        mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
        mPoiDisplay.expandSuggestions();

        //Remove Add-Favor button
        findViewById(R.id.favor_position).setVisibility(View.GONE);
        findViewById(R.id.poi_area_selection_spinner).setEnabled(false);
        findViewById(R.id.poi_area_selection_spinner).setClickable(false);
    }

    @Override
    protected void onDestroy() {
        if (favorHandler != null) favorHandler.storeAllFavorites();
        super.onDestroy();
    }
}
