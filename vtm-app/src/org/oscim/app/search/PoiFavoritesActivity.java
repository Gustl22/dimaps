package org.oscim.app.search;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Window;
import android.widget.ArrayAdapter;

import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.R;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.oscim.app.search.PoiDisplayUtils.getStringListFromPoiList;

/**
 * Created by gustl on 24.03.17.
 */

public class PoiFavoritesActivity extends AppCompatActivity implements PoiSelector {
    private PoiFavoritesHandler favorHandler;
    private List<String> mStringFavorList;
    private List<PointOfInterest> mFavorDataList;
    private ArrayAdapter mFavorListAdapter;

    private PoiDisplayUtils mPoiDisplay;

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
        mPoiDisplay = new PoiDisplayUtils(this);
        mPoiDisplay.poiSelector = this;

        favorHandler = PoiFavoritesHandler.getInstance();
        List<PointOfInterest> actualPois = favorHandler.getFavorites();

        mPoiDisplay.poiSuggestions = actualPois;
        mPoiDisplay.stringSuggestions = getStringListFromPoiList(mPoiDisplay.poiSuggestions);

        mPoiDisplay.suggestionsAdapter.clear();
        mPoiDisplay.suggestionsAdapter.addAll(mPoiDisplay.stringSuggestions);
        mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
        mPoiDisplay.expandSuggestions();
    }

    @Override
    protected void onDestroy() {
        if (favorHandler != null) favorHandler.storeAllFavorites();
        super.onDestroy();
    }

    @Override
    public File getPoiFile(int index) {
        //TODO check this method, the iterator may not has always the same order. so we may use
        // another list instead of collection
        HashMap poiFavorites = favorHandler.getFavoriteHashMap();
        Iterator it = poiFavorites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            List<PointOfInterest> actualPois = (List<PointOfInterest>) pair.getValue();
            if (index > actualPois.size() - 1) {
                index -= actualPois.size();
            } else {
                return new File((String) pair.getKey());
            }
        }
        return null;
    }
}