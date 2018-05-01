package org.rebo.app.route;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.WindowManager;

import com.graphhopper.util.shapes.GHPoint;
import com.woxthebox.draglistview.DragListView;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.poi.storage.PointOfInterest;
import org.rebo.app.App;
import org.rebo.app.R;
import org.rebo.app.debug.RemoteDebugger;
import org.rebo.app.graphhopper.GHPointArea;
import org.rebo.app.search.PoiSearch;
import org.rebo.app.utils.Triplet;

import java.util.ArrayList;
import java.util.List;

import static org.rebo.app.App.activity;
import static org.rebo.app.App.routeSearch;


/**
 * Created by gustl on 24.04.17.
 * Shows actual route points and allow manipulate the list
 */

public class RouteActivity extends AppCompatActivity implements ItemAdapter.DragItemsChangeListener {
    private DragListView mDragListView;
    private List<GHPointArea> mRoutePoints;
    ArrayList<Triplet<Long, String, Integer>> mItemArray;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Debug
        RemoteDebugger.setExceptionHandler(this);

        setContentView(R.layout.activity_route);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        initToolbar();
        initRouteListView();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setTitle("Route points");
        toolbar.setSubtitle("Drag points to change route");
    }

    private void initRouteListView() {
        mItemArray = new ArrayList<>();

        RouteSearch rs = routeSearch;
        if (App.poiManager.getPoiFileId() < 0) {
            activity.showToastOnUiThread("No Poidata found, Download it for geoCoding");
            finish();
        }
        PoiSearch ps = new PoiSearch(App.poiManager);

        GHPointArea rDepart = rs.getStartPoint();
        GHPointArea rDest = rs.getDestinationPoint();
        List<GHPointArea> rVia = rs.getViaPoints();
        mRoutePoints = new ArrayList<>();

        if (rDepart != null) {
            mRoutePoints.add(rDepart);
        } else {
            Location l = App.activity.getLocationHandler().getLastKnownLocation();
            if (l != null) {
                GHPoint ghpoint = new GHPoint(l.getLatitude(), l.getLongitude());
                GHPointArea area = new GHPointArea(ghpoint, RouteSearch.getGraphHopperFiles());
                mRoutePoints.add(area);
            }
        }
        if (rVia != null) {
            mRoutePoints.addAll(rVia);
        }
        if (rDest != null) {
            mRoutePoints.add(rDest);
        }

        int i = 0;
        for (GHPointArea routePoint : mRoutePoints) {
            GHPoint ghPoint = routePoint.getGhPoint();
            LatLong rPoint = new LatLong(ghPoint.getLat(), ghPoint.getLon());
            List<PointOfInterest> poiList = PoiSearch.getPoisByPoint(rPoint, 50, App.poiManager.getPoiFile());
            int imageId = (mRoutePoints.size() == i + 1) ? R.drawable.ic_place_red_24dp :
                    (i == 0 ? R.drawable.ic_place_green_24dp : R.drawable.ic_place_yellow_24dp);
            if (poiList != null && !poiList.isEmpty()) {
                mItemArray.add(new Triplet<>((long) i, poiList.get(0).getName(), imageId));
            } else {
                mItemArray.add(new Triplet<>((long) i, "Lat: " + rPoint.getLatitude()
                        + "; Lon: " + rPoint.getLongitude(), imageId));
            }
            i++;
        }

        mDragListView = (DragListView) findViewById(R.id.routePointList);
        mDragListView.setDragListListener(new DragListView.DragListListener() {
            @Override
            public void onItemDragStarted(int position) {
//                Toast.makeText(getActivity(), "Start - position: " + position, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemDragging(int itemPosition, float x, float y) {
                //
            }

            @Override
            public void onItemDragEnded(int fromPosition, int toPosition) {
                if (fromPosition != toPosition) {
                    //Update RouteSearch
                    GHPointArea area = mRoutePoints.remove(fromPosition);
                    mRoutePoints.add(toPosition, area);

                    updateRoutePoints();
                }
            }
        });

        mDragListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        ItemAdapter listAdapter = new ItemAdapter(mItemArray, R.layout.list_swipe_item, R.id.item_layout, true);
        listAdapter.registerItemChangeListener(this);
        mDragListView.setAdapter(listAdapter, true);
        mDragListView.setCanDragHorizontally(false);
    }

    private void updateRoutePoints() {
        routeSearch.clearOverlays();

        int i = 0;
        for (GHPointArea mRoutePoint : mRoutePoints) {
            if (i == mRoutePoints.size() - 1) {
                routeSearch.setDestinationPoint(mRoutePoint);
            } else if (i == 0) {
                routeSearch.setStartPoint(mRoutePoint);
            } else {
                routeSearch.addViaPoint(mRoutePoint);
            }
            i++;
        }

        //Update Icons
        List<Triplet<Long, String, Integer>> items = mDragListView.getAdapter().getItemList();
        for (i = 0; i < items.size(); i++) {
            items.get(i).third = (items.size() == i + 1) ? R.drawable.ic_place_red_24dp :
                    (i == 0 ? R.drawable.ic_place_green_24dp : R.drawable.ic_place_yellow_24dp);
        }
    }

    private Activity getActivity() {
        return this;
    }

    @Override
    public void onDragItemAdded(int position) {
        //
    }

    @Override
    public void onDragItemRemoved(int position) {
        mRoutePoints.remove(position);
        updateRoutePoints();
    }

    @Override
    protected void onDestroy() {
        updateRoutePoints();
        super.onDestroy();
    }
}


