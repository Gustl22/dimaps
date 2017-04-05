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
import org.oscim.app.R;
import org.oscim.app.utils.CustomAnimationUtils;

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
    private PrintView vSelection_icon;
    private TextView vSelection_category;
    private TextView vSelection_name;
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
                    currentPoiFile = poiSelector.getPoiFile((int) arg3);
                    setResultText(selectedPOI);
                    collapseSuggestions();
                }
            }
        });

        vPoiActions = parent.findViewById(R.id.poi_actions);
        CustomAnimationUtils.collapse(vPoiActions);
        vSelection_icon = (PrintView) parent.findViewById(R.id.poi_selection_icon);
        vSelection_category = (TextView) parent.findViewById(R.id.poi_selection_category);
        vSelection_name = (TextView) parent.findViewById(R.id.poi_selection_name);

        poiHandler = new PoiActionHandler(parent);
        poiHandler.setDestinationButton(parent.findViewById(R.id.destination_position));
        poiHandler.setShareButton(parent.findViewById(R.id.share_position));
        poiHandler.setFavoriteButton(parent.findViewById(R.id.favor_position));
        poiHandler.setDepartureButton(parent.findViewById(R.id.start_position));
        poiHandler.setShowMapButton(parent.findViewById(R.id.show_position));
        poiHandler.setDeleteFavorButton(parent.findViewById(R.id.favor_delete));
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
        String resText = mSelectedPOI.getName();
        for (Tag t : mSelectedPOI.getTags()) {
            resText += "<br/>" + t.key + ": " + t.value;
        }
        if (mSelectedPOI.getCategory().getTitle().equals(PoiSearch.CustomPoiCategory.Maparea.name())) {
            vAreaSelection.setText(Html.fromHtml("<b>" + mSelectedPOI.getCategory().getTitle() + ": </b>" + resText));
        } else {
            vSelection_category.setText(mSelectedPOI.getCategory().getTitle());
            vSelection_name.setText(mSelectedPOI.getName());
            vResult.setText(Html.fromHtml(resText));
            poiHandler.setPoi(mSelectedPOI, currentPoiFile);
            vSelection_icon.setIconText(getIconFromPOI(mSelectedPOI));
            CustomAnimationUtils.expand(vPoiActions);
            if (currentPoiFile != null) {
                vAreaSelection.setText(Html.fromHtml("<b> Maparea: </b>" + currentPoiFile.getName()
                        .substring(0, currentPoiFile.getName().length() - 4).replace("_", ", ")));
            }
        }
    }

    public String getIconFromPOI(PointOfInterest poi) {
        switch (poi.getCategory().getTitle().toLowerCase()) {
            case "restaurants":
                return "ic_restaurant_menu";
            case "cafes":
                return "ic_local_cafe";
            case "bus stations":
                return "ic_directions_bus";
            case "fuel stations":
                return "ic_local_gas_station";
            case "banks":
                return "ic_monetization_on";
            case "cinemas":
                return "ic_local_movies";
            case "public toilets":
                return "ic_wc";
            case "bus stops":
                return "ic_directions_bus";
        }
        for (Tag tag : poi.getTags()) {
            switch (tag.key) {
                case "building":
                    return "ic_home";
                case "highway":
                    switch (tag.value) {
                        case "footway":
                            return "ic_directions_walk";
                    }
                    return "ic_directions_car";
                case "amenity":
                    return "ic_domain";
                case "place":
                    return "ic_location_city";
                case "natural":
                    return "ic_nature_people";
            }
        }
        return "ic_place";
    }

    public static List<String> getStringListFromPoiList(List<PointOfInterest> poiList) {
        List<String> arr = new ArrayList<>();
        for (PointOfInterest poi : poiList) {
            String builder = poi.getName();
            List<Tag> tags = poi.getTags();
            String postcode = "";
            String city = "";
            String is_in = "";
            String street = "";
            for (Tag t : tags) {
                switch (t.key.toLowerCase()) {
                    case "addr:postcode":
                        postcode = ", " + t.value;
                        break;
                    case "addr:city":
                        city = " " + t.value;
                        break;
                    case "addr:street":
                        street = ", " + t.value;
                        break;
                    case "is_in":
                        is_in = t.value;
                        break;
                    default:
                        break;
                }
            }
            if (!is_in.isEmpty()) {
                String[] adArr = is_in.split(",|;");
                is_in = "";
                for (String s : adArr) {
                    if (!s.isEmpty()) {
                        is_in += (", " + s.trim());
                    }
                }
            }
            city = city.isEmpty() ? is_in : (postcode.isEmpty() ? "," + city : city);

            arr.add(builder + street + postcode + city);
        }
        return arr;
    }
}