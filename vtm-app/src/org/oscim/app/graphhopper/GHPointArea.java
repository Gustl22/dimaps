package org.oscim.app.graphhopper;

import android.util.Log;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class GHPointArea{
    private List<GHPointListener> listeners = new ArrayList<GHPointListener>();

    public void addListener(GHPointListener toAdd) {
        listeners.add(toAdd);
    }
    public void updatePoints() {
        // Notify everybody that may be interested.
        for (GHPointListener hl : listeners)
            hl.onRoutePointUpdate();
    }


    private static Collection<GHPointArea> GHPointAreas;
    private GraphHopper graphHopper;
    private GHPoint ghPoint;

    /**
     * An location GHPoint, that stores it's area routing map
     * @param ghPoint The point you want to store
     * @param ghFiles All graphhopper directories, that store data
     * @param overridden If the GHPointArea you override was not null, else type null
     *                   (Background: the point you override must deleted from the internal list,
     *                   so it does not disturb)
     */
    public GHPointArea(GHPoint ghPoint, ArrayList<File> ghFiles, GHPointArea overridden, GHPointListener rpl){
        if(GHPointAreas == null)
            GHPointAreas = new HashSet<GHPointArea>();
        this.ghPoint = ghPoint;
        if(overridden != null){
            GHPointArea.getGHPointAreas().remove(overridden);
        }
        GHPointAreas.add(this);
        addListener(rpl);
        new GHAsyncTask<Object, Void, GraphHopper>(){
            @Override
            protected GraphHopper saveDoInBackground(Object... params) throws Exception {
                return autoSelectGraphhopper((GHPoint) params[0], GHPointAreas, (ArrayList<File>) params[1]);
            }

            protected void onPostExecute(GraphHopper hopper){
                graphHopper = hopper;
                updatePoints();
            }
        }.execute(ghPoint, ghFiles);
    }

    /**
     * Manually add Point to Graphhopper e.g. on edges
     * @param hopper Predefined graphhopper
     */
    public GHPointArea(GHPoint ghPoint, GraphHopper hopper, GHPointArea overridden, GHPointListener rpl){
        if(GHPointAreas == null)
            GHPointAreas = new HashSet<GHPointArea>();
        this.ghPoint = ghPoint;
        addListener(rpl);
        if(overridden != null){
            GHPointArea.getGHPointAreas().remove(overridden);
        }
        GHPointAreas.add(this);
        this.graphHopper = hopper;
        updatePoints();
    }

    public static Collection<GHPointArea> getGHPointAreas(){
        return GHPointAreas;
    }

    public static void setGHPointAreas(Collection<GHPointArea> ghPointAreas){
        GHPointAreas = ghPointAreas;
    }

    public GraphHopper getGraphHopper(){
        return graphHopper;
    }

    /**
     * Automatically Selects the Graphhopper map, based on its location
     * @param pt The point you want its Routing map
     * @param ghPointAreas Areas, which were already loaded by other Points
     * @param ghFiles All areas, which can be loaded, if ghPointAreas don't match
     * @return GraphHopper map in which the point is located.
     */
    public GraphHopper autoSelectGraphhopper(GHPoint pt, Collection<GHPointArea> ghPointAreas,
                                             ArrayList<File> ghFiles){
                //Search Point in already loaded areas
                for(GHPointArea ghpa : ghPointAreas){
                    GraphHopper gh = ghpa.getGraphHopper();
                    if(gh != null) {
                        try{
                            QueryResult qr = gh.getLocationIndex().findClosest(pt.getLat(), pt.getLon(),
                                    EdgeFilter.ALL_EDGES);
                            if (qr.getClosestNode() > 0) {
                                return gh;
                            }
                        } catch (IllegalStateException ex) {
                            Log.e(ex.getMessage(),ex.getCause().getMessage());
                        }
                    }
                }

                //Calculation will be in different area
                fileloop:
                for(File f : ghFiles) {
                    //Exclude already loaded areas
                    for(GHPointArea ghpa : ghPointAreas){
                        GraphHopper gh = ghpa.getGraphHopper();
                        if(gh != null){
                            String fPath = new File(f.getAbsolutePath()).getAbsolutePath();
                            String hPath = new File(gh.getGraphHopperStorage().getDirectory().getLocation()).getAbsolutePath();
                            if(fPath.equals(hPath))
                                continue fileloop;
                        }
                    }
                    try {
                        GraphHopper graphHopper = GHAsyncLoader.loadGraphhopperStorage(f.getAbsolutePath());
                        QueryResult qr = graphHopper.getLocationIndex().findClosest(pt.getLat(), pt.getLon(),
                                EdgeFilter.ALL_EDGES);
                        if (qr.getClosestNode() > 0) {
                            return graphHopper;
                        }
                    } catch (Exception ex) {
                        if(ex instanceof IllegalStateException){
                            Log.e(ex.getMessage(),ex.getCause().getMessage());
                        }
                        continue;
                    }
                }
                return null;
            }

    public GHPoint getGhPoint() {
        return ghPoint;
    }

    public void setGhPoint(GHPoint ghPoint) {
        this.ghPoint = ghPoint;
    }
}
