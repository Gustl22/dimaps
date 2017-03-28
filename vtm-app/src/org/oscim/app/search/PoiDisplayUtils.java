package org.oscim.app.search;

import android.app.Activity;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;

import org.mapsforge.core.model.Tag;
import org.mapsforge.poi.storage.PointOfInterest;
import org.oscim.app.utils.CustomAnimationUtils;
import org.oscim.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 27.03.17.
 */

public class PoiDisplayUtils {
    public TextView vAreaSelection;
    public TextView vResult;
    public PrintView vExpandButton;
    public View vPoiActions;
    public PoiActionHandler poiHandler;
    public ListView vPoiListView;
    public List<String> stringSuggestions;
    public ArrayAdapter<String> suggestionsAdapter;
    public List<PointOfInterest> poiSuggestions;
    public PointOfInterest selectedPOI;
    public File currentPoiFile;

    public PoiSelector poiSelector;
    private Activity mParent;

    public PoiDisplayUtils(Activity parent) {
        onCreateView(parent);
    }

    public void onCreateView(Activity parent) {

        LinearLayout mExpandLine;
        this.mParent = parent;
        //Set search-Bar on-hit-Enter-Listener
        stringSuggestions = new ArrayList<>();
        stringSuggestions.add("No suggestions");
        vResult = (TextView) parent.findViewById(R.id.poi_selection_textview);
        vPoiListView = (ListView) parent.findViewById(R.id.poi_listview);
        mExpandLine = (LinearLayout) parent.findViewById(R.id.expand_line);
        vExpandButton = (PrintView) parent.findViewById(R.id.result_expand_btn);
        mExpandLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vPoiListView.getVisibility() == View.GONE) {
                    expandSuggestions();
                } else {
                    collapseSuggestions();
                }
            }
        });
        vAreaSelection = (TextView) parent.findViewById(R.id.poi_area_selection_textview);

        //Set autocompletion-List
        suggestionsAdapter = new ArrayAdapter<String>
                (parent, R.layout.simple_dropdown_item_1line, stringSuggestions);
        vPoiListView.setAdapter(suggestionsAdapter);
        //Onclick suggested item
        vPoiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                if (poiSuggestions != null && !poiSuggestions.isEmpty()) {
                    selectedPOI = poiSuggestions.get((int) arg3);
                    setResultText(selectedPOI);
                    currentPoiFile = poiSelector.getPoiFile((int) arg3);
                    collapseSuggestions();
                }
            }
        });

        vPoiActions = parent.findViewById(R.id.poi_actions);
        CustomAnimationUtils.collapse(vPoiActions);

        poiHandler = new PoiActionHandler(parent);
        poiHandler.setDestinationButton(parent.findViewById(R.id.destination_position));
        poiHandler.setMvShareButton(parent.findViewById(R.id.share_position));
        poiHandler.setMvFavoriteButton(parent.findViewById(R.id.favor_position));
        poiHandler.setMvStartButton(parent.findViewById(R.id.start_position));
        poiHandler.setMvShowMapButton(parent.findViewById(R.id.show_position));
    }

    public void collapseSuggestions() {
        CustomAnimationUtils.collapse(vPoiListView);
        vExpandButton.setIconText(mParent.getString(R.string.ic_keyboard_arrow_right));
    }

    public void expandSuggestions() {
        CustomAnimationUtils.expand(vPoiListView);
        vExpandButton.setIconText(mParent.getString(R.string.ic_keyboard_arrow_down));
    }

    public void setResultText(PointOfInterest mSelectedPOI) {
        String resText = "<b>" + mSelectedPOI.getCategory().getTitle() + ": </b>" + mSelectedPOI.getName();
        for (Tag t : mSelectedPOI.getTags()) {
            resText += "<br/>" + t.key + ": " + t.value;
        }
        if (mSelectedPOI.getCategory().getTitle().equals(PoiSearch.CustomPoiCategory.Maparea.name())) {
            vAreaSelection.setText(Html.fromHtml(resText));
        } else {
            vResult.setText(Html.fromHtml(resText));
            poiHandler.setPoi(mSelectedPOI, currentPoiFile);
            CustomAnimationUtils.expand(vPoiActions);
            if (currentPoiFile != null) {
                vAreaSelection.setText(Html.fromHtml("<b> Maparea: </b>" + currentPoiFile.getName()
                        .substring(0, currentPoiFile.getName().length() - 4).replace("_", ", ")));
            }
        }
    }

    public static List<String> getStringListFromPoiList(List<PointOfInterest> poiList) {
        List<String> arr = new ArrayList<>();
        for (PointOfInterest poi : poiList) {
            String builder = poi.getName();
            List<Tag> tags = poi.getTags();
            for (Tag t : tags) {
                switch (t.key) {
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
}