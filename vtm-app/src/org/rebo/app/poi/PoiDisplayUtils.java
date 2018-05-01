package org.rebo.app.poi;

import android.app.Activity;
import android.text.Html;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.johnkil.print.PrintView;

import org.mapsforge.core.model.Tag;
import org.mapsforge.poi.storage.PoiCategory;
import org.mapsforge.poi.storage.PointOfInterest;
import org.rebo.app.R;
import org.rebo.app.holder.AreaFileInfo;
import org.rebo.app.utils.CustomAnimationUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by gustl on 27.03.17.
 */

public class PoiDisplayUtils {
    public Spinner vAreaSpinner;
    public TextView vResult;
    public PrintView vExpandButton;
    public View vPoiActions;
    public PoiActionHandler poiHandler;
    public ListView vPoiListView;
    private LinearLayout vmSelection_icon_wrapper;
    private TextView vSelection_category;
    private TextView vSelection_name;
    public List<QuickSearchListItem> searchSpinnerItems = new ArrayList<>();
    public final List<String> areaSpinnerItems = new ArrayList<>();
    public ArrayAdapter<QuickSearchListItem> suggestionsAdapter;
    public final List<PointOfInterest> poiSuggestions = new ArrayList<>();
    public PointOfInterest selectedPOI;
    public int mPoiFileId;

    private final PoiManager mPoiManager;
    private final PoiFavoritesHandler mPoiFavoritesHandler;

    private Activity mParent;

    public PoiDisplayUtils(Activity parent, PoiManager manager, PoiFavoritesHandler poiFavorHandler) {
        this.mPoiManager = manager;
        this.mPoiFavoritesHandler = poiFavorHandler;
        onCreateView(parent);
    }

    public void onCreateView(Activity parent) {

        LinearLayout mExpandLine;
        this.mParent = parent;
        //Set search-Bar on-hit-Enter-Listener

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
        vAreaSpinner = parent.findViewById(R.id.poi_area_selection_spinner);

        //Set autocompletion-List
        suggestionsAdapter = new QuickSearchListAdapter(parent, searchSpinnerItems);
        vPoiListView.setAdapter(suggestionsAdapter);
        //Onclick suggested item
        vPoiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!poiSuggestions.isEmpty()) {
                    selectedPOI = poiSuggestions.get((int) id);
                    mPoiFileId = mPoiManager.getPoiFileId();
                    updateResults(selectedPOI);
                    collapseSuggestions();
                }
            }
        });

        vPoiActions = parent.findViewById(R.id.poi_actions);
        CustomAnimationUtils.collapse(vPoiActions);
        vmSelection_icon_wrapper = (LinearLayout) parent.findViewById(R.id.poi_selection_icon_wrapper);
        vSelection_category = (TextView) parent.findViewById(R.id.poi_selection_category);
        vSelection_name = (TextView) parent.findViewById(R.id.poi_selection_name);

        poiHandler = new PoiActionHandler(parent, mPoiManager, mPoiFavoritesHandler);
        poiHandler.setDestinationButton(parent.findViewById(R.id.destination_position));
        poiHandler.setShareButton(parent.findViewById(R.id.share_position));
        poiHandler.setFavoriteButton(parent.findViewById(R.id.favor_position));
        poiHandler.setDepartureButton(parent.findViewById(R.id.start_position));
        poiHandler.setShowMapButton(parent.findViewById(R.id.show_position));
        poiHandler.setDeleteFavorButton(parent.findViewById(R.id.favor_delete));

        SparseArray<File> poiFiles = mPoiManager.getPoiFiles();
        if (poiFiles.size() == 0) {
            areaSpinnerItems.add(0, "No POI files downloaded");
        } else {
            for (int i = 0; i < poiFiles.size(); i++) {
                areaSpinnerItems.add(poiFiles.keyAt(i),
                        new AreaFileInfo(poiFiles.valueAt(i).getPath()).toString());
            }
        }

        ArrayAdapter<String> areaSpinnerAdapter = new ArrayAdapter<>(parent,
                android.R.layout.simple_spinner_dropdown_item, areaSpinnerItems);
        vAreaSpinner.setAdapter(areaSpinnerAdapter);
        vAreaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                mPoiManager.setPoiFileId((int) id);
                mPoiFileId = mPoiManager.getPoiFileId();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        vAreaSpinner.setSelection(mPoiManager.getPoiFileId());
    }

    public void collapseSuggestions() {
        CustomAnimationUtils.collapse(vPoiListView);
        vExpandButton.setIconText(mParent.getString(R.string.ic_keyboard_arrow_right));
    }

    public void expandSuggestions() {
        CustomAnimationUtils.expand(vPoiListView);
        vExpandButton.setIconText(mParent.getString(R.string.ic_keyboard_arrow_down));
    }

    public void updateResults(PointOfInterest mSelectedPOI) {
        String resText = mSelectedPOI.getName();
        for (Tag t : mSelectedPOI.getTags()) {
            resText += "<br/>" + t.key + ": " + t.value;
        }

        vmSelection_icon_wrapper.removeAllViews();
        StringBuilder sb = null;
        for (PoiCategory poiCategory : mSelectedPOI.getCategories()) {
            String icon = getIconFromCategory(poiCategory);
            if (sb == null) {
                sb = new StringBuilder();
            } else {
                sb.append("\n");
            }
            sb.append(poiCategory.getTitle());

            PrintView v = (PrintView) LayoutInflater.from(mParent).inflate(R.layout.poi_icon, null);
            v.setIconText(icon);
            vmSelection_icon_wrapper.addView(v);

            // Needed to update, after animation has finished.
            v.postInvalidateDelayed(100);
        }

        assert sb != null;
        vSelection_category.setText(sb.toString());

        vSelection_name.setText(mSelectedPOI.getName());
        vResult.setText(Html.fromHtml(resText));
        poiHandler.setPoi(mSelectedPOI, mPoiFileId);

        CustomAnimationUtils.expand(vPoiActions);
    }

    public static String getIconFromCategory(PoiCategory cat) {
        assert cat != null;
        List<PoiCategory> path = new ArrayList<PoiCategory>();
        PoiCategory parent = cat;
        do {
            path.add(parent);
        } while ((parent = parent.getParent()) != null && (!parent.getTitle().equals("root")));
        Collections.reverse(path);

        List<String> catsStrings = new ArrayList<>();
        for (PoiCategory poiCategory : path) {
            catsStrings.add(poiCategory.getTitle().toLowerCase());
        }

        switch (catsStrings.get(0)) {
            case "amenities":
                switch (catsStrings.get(1)) {
                    case "food":
                        switch (catsStrings.get(2)) {
                            case "fast food restaurants":
                                return "ic_local_pizza";
                            case "cafes":
                                return "ic_local_cafe";
                        }
                        return "ic_local_dining";
                    case "transportation":
                        switch (catsStrings.get(2)) {
                            case "bus stations":
                                return "ic_directions_bus";
                            case "fuel stations":
                                return "ic_local_gas_station";
                            case "car parks":
                                return "ic_local_parking";
                        }
                        return "ic_directions_car";
                    case "financial institutes":
                        return "ic_monetization_on";
                    case "education institutes":
                        switch (catsStrings.get(2)) {
                            case "public libraries":
                                return "ic_local_library";
                        }
                        return "ic_school";
                    case "entertainment, arts and culture":
                        switch (catsStrings.get(2)) {
                            case "cinemas":
                                return "ic_local_movies";
                        }
                        return "ic_local_play";
                    case "health care":
                        return "ic_local_hospital";
                    case "other amenities":
                        switch (catsStrings.get(2)) {
                            case "public toilets":
                                return "ic_wc";
                        }
                        return "ic_local_activity";
                }
                return "ic_domain";
            case "airport pois":
                return "ic_local_airport";
            case "barriers":
                return "ic_do_not_disturb_on";
            case "crafting":
                return "ic_build";
            case "emergency":
                return "ic_local_hospital";
            case "geological":
                return "ic_terrain";
            case "highway":
                switch (catsStrings.get(1)) {
                    case "bus stops":
                        return "ic_directions_bus";
                }
                return "ic_timeline";
            case "historic":
                return "ic_search";
            case "leisure":
                return "ic_local_activity";
            case "man made":
                switch (catsStrings.get(1)) {
                    case "beacons":
                        return "ic_lightbulb_outline";
                    case "works":
                        return "ic_work";
                }
                return "ic_work";
            case "military":
                return "ic_warning";
            case "natural":
                return "ic_nature_people";
            case "office":
                return "ic_attach_file";
            case "places":
                return "ic_location_city";
            case "public transport":
                return "ic_transfer_within_a_station";
            case "railway": // TODO
                return "ic_directions_subway";
            case "shop":
                switch (catsStrings.get(1)) {
                    case "toys":
                        return "ic_toys";
                    case "newsagents":
                        return "ic_work";
                }
                return "ic_local_grocery_store";
            case "sport":
                return "ic_fitness_center";
            case "tourism":
                switch (catsStrings.get(1)) {
                    case "museums":
                        return "ic_account_balance";
                    case "information":
                        return "ic_information_outline";
                }
                return "ic_photo_camera";
            case "waterways":
                return "ic_pool";
            case "bicycle related":
                return "ic_directions_bike";
        }
//        for (Tag tag : poi.getTags()) {
//            switch (tag.key) {
//                case "highway":
//                    switch (tag.value) {
//                        case "footway":
//                            return "ic_directions_walk";
//                    }
//            }
//        }
        return "ic_place";
    }

    public static List<QuickSearchListItem> getSearchItemListFromPoiList(List<PointOfInterest> poiList) {
        List<QuickSearchListItem> arr = new ArrayList<>();
        for (PointOfInterest poi : poiList) {
            String name = poi.getName();
            Set<Tag> tags = poi.getTags();
            String postcode = "";
            String city = "";
            String is_in = "";
            String street = "";
            for (Tag t : tags) {
                switch (t.key.toLowerCase()) {
                    case "addr:postcode":
                        postcode = t.value;
                        break;
                    case "addr:city":
                        city = " " + t.value;
                        break;
                    case "addr:street":
                        street = t.value + ", ";
                        break;
                    case "is_in":
                        is_in = t.value;
                        break;
                    default:
                        break;
                }
            }
            QuickSearchListItem item = new QuickSearchListItem(name);
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
            if (poi.getCategory() != null) {
                item.setCategory(poi.getCategory().getTitle());
                item.setCategoryIcon(getIconFromCategory(poi.getCategory()));
            } else {
                item.setCategory("No category available");
                item.setCategoryIcon("root");
            }
            item.setDistance("0m");
            item.setTown(street + postcode + city);
            arr.add(item);
        }
        return arr;
    }
}