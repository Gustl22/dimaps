package org.rebo.app.poi;

import android.content.Context;
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
import org.rebo.app.App;
import org.rebo.app.R;
import org.rebo.app.debug.RemoteDebugger;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gustl on 17.03.17.
 */

public class PoiSearchActivity extends AppCompatActivity {
    private AutoCompleteTextView mSearchBar;
    private PoiSearch mPoiSearch;
    private PoiDisplayUtils mPoiDisplay;
    private ProgressBar mSearchProgress;
    private SearchTask mSearchTask;
    private final PoiManager mPoiManager = App.poiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        //Debug
        RemoteDebugger.setExceptionHandler(this);

        setContentView(R.layout.activity_poi_search);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (mPoiManager.getPoiFileId() < 0 || mPoiManager.getPoiFiles().size() == 0) {
            App.activity.showToastOnUiThread("No POI data found. Download it first");
            finish();
        }
        mPoiManager.loadPreferences(this);

        initPoiDisplay(); //Keep order
        initPoiSearchBar();
        //Both initializations refer to each other.
    }

    private void initPoiDisplay() {
        mPoiDisplay = new PoiDisplayUtils(this, mPoiManager, new PoiFavoritesHandler(mPoiManager));
        mPoiDisplay.suggestionsAdapter.add(new QuickSearchListItem("No proposals"));
        mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
        mPoiDisplay.collapseSuggestions();
        //Remove Delete-Favor button
        findViewById(R.id.favor_delete).setVisibility(View.GONE);
    }

    private void initPoiSearchBar() {
        //SearchBar Events
        mSearchBar = (AutoCompleteTextView) findViewById(R.id.search_bar);
        mSearchBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                updateSuggestions(v.getText().toString());
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
            @Override
            public void afterTextChanged(Editable s) {
//                if (mSearchTask != null) mSearchTask.cancel(true);
//                getSuggestions((mSearchBar.getText().toString()));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // you can check for enter key here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        //Set ProgressBar
        mSearchProgress = (ProgressBar) findViewById(R.id.search_progress);


        //Setup search logic
        mPoiSearch = new PoiSearch(mPoiManager);
    }

    /**
     * Gets suggestion-list of all categories filtered by text
     * @param text input-filter for poi-text
     */

    private void updateSuggestions(String text) {
        if (mSearchTask != null)
            mSearchTask.cancel(true);
        mSearchTask = new SearchTask();
        mSearchTask.execute(text);
    }

    private class SearchTask extends AsyncTask<String, Void, ArrayList<PointOfInterest>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mSearchProgress.setIndeterminate(true);
            mSearchProgress.setVisibility(View.VISIBLE);
        }

        @Override
        protected ArrayList<PointOfInterest> doInBackground(String... params) {
            Collection<PointOfInterest> pois = mPoiSearch.getPoiByAll(params[0]);
            return new ArrayList<>(pois);
        }

        @Override
        protected void onPostExecute(ArrayList<PointOfInterest> pointOfInterests) {
            mSearchProgress.setVisibility(View.GONE);

            super.onPostExecute(pointOfInterests);
            mPoiDisplay.poiSuggestions.clear();
            mPoiDisplay.poiSuggestions.addAll(pointOfInterests);

            // Must be newly set, otherwise no updates are visible
            mPoiDisplay.searchSpinnerItems =
                    PoiDisplayUtils.getSearchItemListFromPoiList(mPoiDisplay.poiSuggestions);

            mPoiDisplay.suggestionsAdapter.clear();
            mPoiDisplay.suggestionsAdapter.addAll(mPoiDisplay.searchSpinnerItems);
            mPoiDisplay.suggestionsAdapter.notifyDataSetChanged();
            mPoiDisplay.expandSuggestions();
//                mAutoCompleteSearchBarAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onCancelled() {
            PoiSearch.closePoiPersistenceManagers();
        }
    }

    @Override
    protected void onDestroy(){
        mSearchProgress.setVisibility(View.GONE);
        mPoiManager.savePreferences(this);
        super.onDestroy();
    }

    public void finish(View view) {
        finish();
    }
}