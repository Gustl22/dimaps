package org.oscim.app.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mapsforge.poi.storage.PointOfInterest;
import org.openstreetmap.osmosis.osmbinary.file.FileFormatException;
import org.oscim.app.App;
import org.oscim.app.R;
import org.oscim.app.debug.RemoteDebugger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;

import static org.oscim.app.search.PoiDisplayUtils.getSearchItemListFromPoiList;

/**
 * Created by gustl on 17.03.17.
 */

public class PoiSearchActivity extends AppCompatActivity {
    private AutoCompleteTextView mSearchBar;
    private PoiSearch mPoiSearch;
    private PoiDisplayUtils mPoiDisplay;
    private ProgressBar mSearchProgress;
    private AsyncTask mSearchTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        //Debug
        RemoteDebugger.setExceptionHandler(this);

        setContentView(R.layout.activity_poi_search);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        initPoiDisplay(); //Keep order
        initPoiSearchBar();
        //Both initializations refer to each other.
        // So the poiSelector is set afterwards
        mPoiDisplay.poiSelector = mPoiSearch;
        loadPreferences();
    }

    private void initPoiDisplay() {
        mPoiDisplay = new PoiDisplayUtils(this);
        //Remove Delete-Favor button
        findViewById(R.id.favor_delete).setVisibility(View.GONE);
    }

    private void initPoiSearchBar() {
        //SearchBar Events
        mSearchBar = (AutoCompleteTextView) findViewById(R.id.search_bar);
        mSearchBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (mSearchTask != null) mSearchTask.cancel(true);
                getSuggestions(v.getText().toString());
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                return true;
            }
        });
        //Set real-time key-listener

        mSearchBar.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
//                if (mSearchTask != null) mSearchTask.cancel(true);
//                getSuggestions((mSearchBar.getText().toString()));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // you can check for enter key here
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        //Set ProgressBar
        mSearchProgress = (ProgressBar) findViewById(R.id.search_progress);


        //Setup search logic
        mPoiSearch = new PoiSearch();
        try {
            if (mPoiDisplay.currentPoiFile == null) {
                mPoiSearch.initPoiFile();
            } else {
                mPoiSearch.setPoiFile(mPoiDisplay.currentPoiFile);
            }
        } catch (FileNotFoundException ex) {
            App.activity.showToastOnUiThread("No POI data found. Download it first");
            finish();
        } catch (FileFormatException ex) {
            App.activity.showToastOnUiThread(ex.getMessage());
            finish();
        }
        if(mPoiSearch.getPoiArea() != null){
            mPoiDisplay.currentPoiFile = mPoiSearch.getPoiFile(0);
            mPoiDisplay.setResultText(mPoiSearch.getPoiArea());
        }
    }

    /**
     * Gets suggestion-list of all categories filtered by text
     * @param text input-filter for poi-text
     * @return List of suggestions
     */

    private void getSuggestions(String text){
        final Context context = this;
        mSearchTask = new AsyncTask<String, Void, ArrayList<PointOfInterest>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mSearchProgress.setIndeterminate(true);
                mSearchProgress.setVisibility(View.VISIBLE);
            }

            @Override
            protected ArrayList<PointOfInterest> doInBackground(String... params) {
                try {
                    Collection<PointOfInterest> pois = mPoiSearch.getPoiByAll(params[0]);
                    return new ArrayList<>(pois);
                } catch (FileFormatException e) {
                    App.activity.showToastOnUiThread(e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ArrayList<PointOfInterest> pointOfInterests) {
                mSearchProgress.setVisibility(View.GONE);

                super.onPostExecute(pointOfInterests);
                mPoiDisplay.poiSuggestions = pointOfInterests;
                mPoiDisplay.listItemSuggestions = getSearchItemListFromPoiList(mPoiDisplay.poiSuggestions);

                mPoiDisplay.suggestionsAdapter.clear();
                mPoiDisplay.suggestionsAdapter.addAll(mPoiDisplay.listItemSuggestions);
                mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
                mPoiDisplay.expandSuggestions();
//                mAutoCompleteSearchBarAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onCancelled() {
                PoiSearch.closePoiPersistenceManagers();
            }
        }.execute(text);
    }

    public void loadPreferences() {
        SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
        String filepath = sharedPref.getString(getString(R.string.pref_poiArea_folderPath), null);
        if(filepath != null)
            mPoiDisplay.currentPoiFile = new File(filepath);
    }

    public void savePreferences(){
        if (mPoiDisplay.currentPoiFile != null) {
            SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(getString(R.string.pref_poiArea_folderPath), mPoiDisplay.currentPoiFile.getAbsolutePath());
            editor.apply();
        }
    }

    @Override
    protected void onDestroy(){
        mSearchProgress.setVisibility(View.GONE);
        savePreferences();
        super.onDestroy();
    }

    public void finish(View view) {
        finish();
    }
}