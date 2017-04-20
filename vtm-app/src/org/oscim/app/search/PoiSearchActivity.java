package org.oscim.app.search;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.App;
import org.oscim.app.R;
import org.oscim.app.debug.RemoteDebugger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import static org.oscim.app.search.PoiDisplayUtils.getSearchItemListFromPoiList;

/**
 * Created by gustl on 17.03.17.
 */

public class PoiSearchActivity extends AppCompatActivity {
    private AutoCompleteTextView mSearchBar;
    private PoiSearch mPoiSearch;
    private PoiDisplayUtils mPoiDisplay;

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
        mSearchBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                return false;
            }
        });


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
    ProgressDialog progDialog;
    private void getSuggestions(String text){
        final Context context = this;
        new AsyncTask<String, Void, ArrayList<PointOfInterest>>(){
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progDialog = new ProgressDialog(context);
                progDialog.setMessage("Loading...");
                progDialog.setIndeterminate(false);
                progDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progDialog.setCancelable(false);
                progDialog.show();
            }

            @Override
            protected ArrayList<PointOfInterest> doInBackground(String... params) {
                return new ArrayList<>(mPoiSearch.getPoiByAll(params[0]));
            }

            @Override
            protected void onPostExecute(ArrayList<PointOfInterest> pointOfInterests) {
                if (progDialog != null)
                    progDialog.dismiss();
                super.onPostExecute(pointOfInterests);
                mPoiDisplay.poiSuggestions = pointOfInterests;
                mPoiDisplay.listItemSuggestions = getSearchItemListFromPoiList(mPoiDisplay.poiSuggestions);

                mPoiDisplay.suggestionsAdapter.clear();
                mPoiDisplay.suggestionsAdapter.addAll(mPoiDisplay.listItemSuggestions);
                mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
                mPoiDisplay.expandSuggestions();
//                mAutoCompleteSearchBarAdapter.notifyDataSetChanged();
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
        if (progDialog != null)
            progDialog.dismiss();
        savePreferences();
        super.onDestroy();
    }

    public void finish(View view) {
        finish();
    }
}