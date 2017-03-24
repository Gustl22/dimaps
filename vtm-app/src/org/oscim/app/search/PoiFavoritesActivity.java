package org.oscim.app.search;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 24.03.17.
 */

public class PoiFavoritesActivity extends AppCompatActivity {
    private PoiFavoritesHandler favorHandler;
    private ListView favorListView;
    private List<String> mStringFavorList;
    private List<PointOfInterest> mFavorDataList;
    private ArrayAdapter mFavorListAdapter;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_poi_favorites);


        favorListView = (ListView) findViewById(R.id.favor_listview);
        favorListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });

        mStringFavorList = new ArrayList<String>();
        favorHandler = PoiFavoritesHandler.getInstance();
        List<PointOfInterest> actualPois = favorHandler.getFavorites();
        for (PointOfInterest poi : actualPois) {
            mStringFavorList.add(poi.getName());
        }
        mFavorListAdapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_dropdown_item_1line, mStringFavorList);
        favorListView.setAdapter(mFavorListAdapter);
        initToolbar();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setTitle("Favorites");
        //toolbar.setSubtitle("OpenDimensionMaps " + version + "!");
    }

    @Override
    protected void onDestroy() {
        if (favorHandler != null) favorHandler.storeAllFavorites();
        super.onDestroy();
    }
}
