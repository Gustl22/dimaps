package org.oscim.app.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import org.mapsforge.core.model.Tag;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.App;
import org.oscim.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 17.03.17.
 */

public class SearchActivity extends AppCompatActivity {
    AutoCompleteTextView mSearchBar;
    File mCurrentPoiFile;
    List<PointOfInterest> mPoiSuggestions;
    List<String> mStringSuggestions;
    ArrayAdapter<String> mAutoCompleteSearchBarAdapter;
    PoiSearch mPoiSearch;
    PointOfInterest mSelectedPOI;
    TextView mResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        loadPreferences();
        setContentView(R.layout.activity_search);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //Set search-Bar on-hit-Enter-Listener
        mStringSuggestions = new ArrayList<>();
        mStringSuggestions.add("No suggestions");
        mResult = (TextView) findViewById(R.id.result);
        mSearchBar = (AutoCompleteTextView) findViewById(R.id.search_bar);
        mSearchBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                mPoiSuggestions = getSuggestions(v.getText().toString());
                mStringSuggestions = getStringListFromPoiList(mPoiSuggestions);

                mAutoCompleteSearchBarAdapter.clear();
                for (String s : mStringSuggestions) {
                    mAutoCompleteSearchBarAdapter.add(s);
                }
                //Force the adapter to filter itself, necessary to show new data.
                //Filter based on the current text because api call is asynchronous.
                mAutoCompleteSearchBarAdapter.getFilter().filter(mSearchBar.getText(), null);
//                mAutoCompleteSearchBarAdapter.notifyDataSetChanged();
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
        //Set autocompletion-List
        mAutoCompleteSearchBarAdapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_dropdown_item_1line, mStringSuggestions);
        mSearchBar.setAdapter(mAutoCompleteSearchBarAdapter);
        //Onclick suggested item
        mSearchBar.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                mSelectedPOI = mPoiSuggestions.get((int) arg3);
                setResultText(mSelectedPOI);
                mCurrentPoiFile = mPoiSearch.getPoiFile();
            }
        });

        //Setup search logic
        mPoiSearch = new PoiSearch(mCurrentPoiFile);
        if(mPoiSearch.getPoiArea() != null){
            mCurrentPoiFile = mPoiSearch.getPoiFile();
            setResultText(mPoiSearch.getPoiArea());
        }
    }

    private void setResultText(PointOfInterest mSelectedPOI) {
        String resText = mSelectedPOI.getName() + " "+ mSelectedPOI.getCategory().getTitle();
        for(Tag t:mSelectedPOI.getTags()){
            resText += "<br/>"+t.key+": "+ t.value;
        }
        mResult.setText(resText);
    }

    /**
     * Gets suggestion-list of all categories filtered by text
     * @param text input-filter for poi-text
     * @return List of suggestions
     */
    private List<PointOfInterest> getSuggestions(String text){
        ArrayList<PointOfInterest> list = new ArrayList<>(mPoiSearch.getPoiByAll(text));
            return list;
    }

    private List<String> getStringListFromPoiList(List<PointOfInterest> poiList){
        List<String> arr = new ArrayList<>();
        for(PointOfInterest poi: poiList){
            arr.add(poi.getName());
        }
        return arr;
    }

    public void loadPreferences() {
        SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
        String filepath = sharedPref.getString(getString(R.string.pref_poiArea_folderPath), null);
        if(filepath != null)
            mCurrentPoiFile = new File(filepath);
    }

    public void savePreferences(){
        if(mCurrentPoiFile != null){
            SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(getString(R.string.pref_poiArea_folderPath), mCurrentPoiFile.getAbsolutePath());
            editor.apply();
        }
    }

    @Override
    protected void onDestroy(){
        savePreferences();
        super.onDestroy();
    }

    public void finish(View view) {
        finish();
    }
}