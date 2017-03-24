package org.oscim.app.search;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;

import org.mapsforge.core.model.Tag;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.App;
import org.oscim.app.CustomAnimationUtils;
import org.oscim.app.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 17.03.17.
 */

public class PoiSearchActivity extends AppCompatActivity {
    private AutoCompleteTextView mSearchBar;
    private File mCurrentPoiFile;
    private List<PointOfInterest> mPoiSuggestions;
    private List<String> mStringSuggestions;
    private ArrayAdapter<String> mSuggestionsAdapter;
    private PoiSearch mPoiSearch;
    private PointOfInterest mSelectedPOI;
    private TextView mResult;
    private TextView mAreaSelection;
    private ListView mSearchSuggestions;
    private PrintView mExpandButton;
    private LinearLayout mExpandLine;
    private PoiActionHandler poiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        loadPreferences();
        setContentView(R.layout.activity_poi_search);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //Set search-Bar on-hit-Enter-Listener
        mStringSuggestions = new ArrayList<>();
        mStringSuggestions.add("No suggestions");
        mResult = (TextView) findViewById(R.id.result);
        mSearchSuggestions = (ListView) findViewById(R.id.search_suggestions);
        mExpandLine = (LinearLayout)  findViewById(R.id.expand_line);
        mExpandButton = (PrintView) findViewById(R.id.result_expand_btn);
        mExpandLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSearchSuggestions.getVisibility() == View.GONE){
                    expandSuggestions();
                } else {
                    collapseSuggestions();
                }
            }
        });
        mAreaSelection = (TextView) findViewById(R.id.poi_area_selection);
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
        //Set autocompletion-List
        mSuggestionsAdapter = new ArrayAdapter<String>
                (this, android.R.layout.simple_dropdown_item_1line, mStringSuggestions);
        mSearchSuggestions.setAdapter(mSuggestionsAdapter);
        //Onclick suggested item
        mSearchSuggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if(mPoiSuggestions != null && !mPoiSuggestions.isEmpty()){
                    mSelectedPOI = mPoiSuggestions.get((int) arg3);
                    setResultText(mSelectedPOI);
                    mCurrentPoiFile = mPoiSearch.getPoiFile();
                    collapseSuggestions();
                }
            }
        });

        //Setup search logic
        mPoiSearch = new PoiSearch();
        try {
            mPoiSearch.setPoiFile(mCurrentPoiFile);
        } catch (FileNotFoundException ex) {
            App.activity.showToastOnUiThread("No POI data found. Download it first");
            finish();
        }
        if(mPoiSearch.getPoiArea() != null){
            mCurrentPoiFile = mPoiSearch.getPoiFile();
            setResultText(mPoiSearch.getPoiArea());
        }

        poiHandler = new PoiActionHandler(this);
        poiHandler.setDestinationButton(findViewById(R.id.destination_position));
        poiHandler.setShareButton(findViewById(R.id.share_position));
        poiHandler.setFavoriteButton(findViewById(R.id.favor_position));
        poiHandler.setStartButton(findViewById(R.id.start_position));
        poiHandler.setShowMapButton(findViewById(R.id.show_position));
    }

    private void setResultText(PointOfInterest mSelectedPOI) {
        String resText = "<b>"+ mSelectedPOI.getCategory().getTitle() +": </b>" + mSelectedPOI.getName();
        for(Tag t:mSelectedPOI.getTags()){
            resText += "<br/>"+t.key+": "+ t.value;
        }
        if(mSelectedPOI.getCategory().getTitle().equals(PoiSearch.CustomPoiCategory.Maparea.name())){
            mAreaSelection.setText(Html.fromHtml(resText));
        } else {
            mResult.setText(Html.fromHtml(resText));
            poiHandler.setPoi(mSelectedPOI, mCurrentPoiFile);
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
                mPoiSuggestions = pointOfInterests;
                mStringSuggestions = getStringListFromPoiList(mPoiSuggestions);

                mSuggestionsAdapter.clear();
                mSuggestionsAdapter.addAll(mStringSuggestions);
                mSuggestionsAdapter.notifyDataSetChanged();
                expandSuggestions();
//                mAutoCompleteSearchBarAdapter.notifyDataSetChanged();
            }
        }.execute(text);
    }

    public void collapseSuggestions(){
        CustomAnimationUtils.collapse(mSearchSuggestions);
        mExpandButton.setIconText(getString(R.string.ic_keyboard_arrow_right));
    }

    public void expandSuggestions(){
        CustomAnimationUtils.expand(mSearchSuggestions);
        mExpandButton.setIconText(getString(R.string.ic_keyboard_arrow_down));
    }

    private List<String> getStringListFromPoiList(List<PointOfInterest> poiList){
        List<String> arr = new ArrayList<>();
        for(PointOfInterest poi: poiList){
            String builder = poi.getName();
            List<Tag> tags = poi.getTags();
            for(Tag t : tags){
                switch(t.key){
                    case "addr:city":
                        builder += ", " + t.value;
                        break;
                    case "addr:street":
                        builder += ", " + t.value;
                        break;
                    default:
                        break;
                }
            }
            arr.add(builder);
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
        if (progDialog != null)
            progDialog.dismiss();
        savePreferences();
        super.onDestroy();
    }

    public void finish(View view) {
        finish();
    }
}