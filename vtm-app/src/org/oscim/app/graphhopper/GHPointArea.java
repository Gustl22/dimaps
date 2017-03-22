package org.oscim.app.graphhopper;

import android.support.v4.os.AsyncTaskCompat;
import android.util.Log;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class GHPointArea {

    private volatile GraphHopper graphHopper;
    private GHPoint ghPoint;
    public final Object virtualObject = new Object();

    /**
     * An location GHPoint, that stores it's area routing map
     *
     * @param ghPoint The point you want to store
     * @param ghFiles All graphhopper directories, that store data
     */
    public GHPointArea(GHPoint ghPoint, ArrayList<File> ghFiles) {
        this.ghPoint = ghPoint;
        Collection<GHPointArea> GHPointAreas = GHPointAreaRoute.getInstance().getGHPointAreas();
        GHAsyncTask<Object, Void, GraphHopper> task = new GHAsyncTask<Object, Void, GraphHopper>() {
            @Override
            protected GraphHopper saveDoInBackground(Object... params) throws Exception {
                GraphHopper hopper = autoSelectGraphhopper((GHPoint) params[0],
                        (Collection<GHPointArea>) params[1], (ArrayList<File>) params[2]);
                return hopper;
            }

            protected void onPostExecute(GraphHopper hopper) {
                graphHopper = hopper;
                synchronized (virtualObject) {
                    virtualObject.notifyAll();
                }
            }
        };
        AsyncTaskCompat.executeParallel(task, ghPoint, GHPointAreas, ghFiles);
    }

    /**
     * Manually add Point to Graphhopper e.g. on edges
     *
     * @param hopper Predefined graphhopper
     */
    public GHPointArea(GHPoint ghPoint, GraphHopper hopper) {
        this.ghPoint = ghPoint;
        this.graphHopper = hopper;
        synchronized (virtualObject) {
            virtualObject.notifyAll();
        }
    }


    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    /**
     * Automatically Selects the Graphhopper map, based on its location
     *
     * @param pt           The point you want its Routing map
     * @param ghPointAreas Areas, which were already loaded by other Points
     * @param ghFiles      All areas, which can be loaded, if ghPointAreas don't match
     * @return GraphHopper map in which the point is located.
     */
    public GraphHopper autoSelectGraphhopper(GHPoint pt, Collection<GHPointArea> ghPointAreas,
                                             ArrayList<File> ghFiles) {
        GraphHopper primaryHopper = GHPointAreaRoute.getInstance().getPrimaryGraphHopper();
        if(isInGraphHopper(primaryHopper, pt)) return primaryHopper;
        //Search Point in already loaded areas
        for (GHPointArea ghpa : ghPointAreas) {
            GraphHopper gh = ghpa.getGraphHopper();
            if(isInGraphHopper(gh, pt)) return gh;
        }

        //Calculation will be in different area
        fileloop:
        for (File f : ghFiles) {
            //Exclude already loaded areas
            for (GHPointArea ghpa : ghPointAreas) {
                GraphHopper gh = ghpa.getGraphHopper();
                if (gh != null) {
                    String fPath = new File(f.getAbsolutePath()).getAbsolutePath();
                    String hPath = new File(gh.getGraphHopperStorage().getDirectory().getLocation()).getAbsolutePath();
                    if (fPath.equals(hPath))
                        continue fileloop;
                }
            }
            try {
                GraphHopper graphHopper = GHAsyncLoader.loadGraphhopperStorage(f.getAbsolutePath());
                if(isInGraphHopper(graphHopper, pt)) return graphHopper;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    public boolean isInGraphHopper(GraphHopper hopper, GHPoint pt){
        if(hopper != null){
            try {
                QueryResult qr = hopper.getLocationIndex().findClosest(pt.getLat(), pt.getLon(),
                        EdgeFilter.ALL_EDGES);
                if (qr.getClosestNode() > 0) {
                    return true;
                }
            } catch (IllegalStateException ex) {
                Log.e(ex.getMessage(), ex.getCause().getMessage());
            }
        }
        return false;
    }

    public GHPoint getGhPoint() {
        return ghPoint;
    }

    public void setGhPoint(GHPoint ghPoint) {
        this.ghPoint = ghPoint;
    }
}
