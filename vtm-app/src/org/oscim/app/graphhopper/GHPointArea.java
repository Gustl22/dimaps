package org.oscim.app.graphhopper;

import android.support.v4.os.AsyncTaskCompat;
import android.util.Log;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class GHPointArea {

    private volatile GraphHopper graphHopper;
    private GHPoint mGhPoint;
    public final Object virtualObject = new Object();
    private static Collection<GraphHopper> sGraphHopperMemory;

    public static Collection<GraphHopper> getGraphHopperMemory() {
        return sGraphHopperMemory;
    }

    public static void setGraphHopperMemory(Collection<GraphHopper> ghMemory) {
        sGraphHopperMemory = ghMemory;
    }

    /**
     * An location GHPoint, that stores it's area routing map
     *
     * @param ghPoint The point you want to store
     * @param ghFiles All graphhopper directories, that store data
     */
    public GHPointArea(GHPoint ghPoint, List<File> ghFiles) {
        this.mGhPoint = ghPoint;
        if (sGraphHopperMemory == null) sGraphHopperMemory = new HashSet<>();
        GHAsyncTask<Object, Void, GraphHopper> task = new GHAsyncTask<Object, Void, GraphHopper>() {
            @Override
            protected GraphHopper saveDoInBackground(Object... params) throws Exception {
                GraphHopper hopper = autoSelectGraphhopper((GHPoint) params[0], (List<File>) params[1],
                        (Collection<GraphHopper>) params[2]);
                return hopper;
            }

            protected void onPostExecute(GraphHopper hopper) {
                graphHopper = hopper;
                synchronized (virtualObject) {
                    virtualObject.notifyAll();
                }
            }
        };
        AsyncTaskCompat.executeParallel(task, ghPoint, ghFiles, sGraphHopperMemory);
    }

    /**
     * Manually add Point to Graphhopper e.g. on edges
     *
     * @param hopper Predefined graphhopper
     */
    public GHPointArea(GHPoint ghPoint, GraphHopper hopper) {
        this.mGhPoint = ghPoint;
        this.graphHopper = hopper;
        synchronized (virtualObject) {
            virtualObject.notifyAll();
        }
    }


    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    public static GraphHopper lastCalledGraphHopper;
    //static final Lock lockSelectGraphhoper = new ReentrantLock(true);
    /**
     * Automatically Selects the Graphhopper map, based on its location
     *
     * @param pt      The point you want its Routing map
     * @param ghFiles All areas, which can be loaded, if ghPointAreas don't match
     * @return GraphHopper map in which the point is located.
     * @paramExt graphHopperMemory Areas, which were already loaded by other Points
     */
    public static synchronized GraphHopper autoSelectGraphhopper(GHPoint pt, List<File> ghFiles,
                                                                 Collection<GraphHopper> ghMemory) {

        Log.w("Hopper", "Lock");
        if (lastCalledGraphHopper != null) {
            if (isInGraphHopper(lastCalledGraphHopper, pt)) {
                return lastCalledGraphHopper;
            }
        }
        //lockSelectGraphhoper.lock();
        //Search Point in already loaded areas
        for (GraphHopper gh : sGraphHopperMemory) {
            if (isInGraphHopper(gh, pt)) {
                if (!gh.equals(lastCalledGraphHopper))
                    lastCalledGraphHopper = gh;
                return gh;
            }
        }
        GraphHopper hopper = null;
        //Calculation will be in different area
        fileloop:
        for (File f : ghFiles) {
            //Exclude already loaded areas
            for (GraphHopper gh : ghMemory) {
                if (gh != null) {
                    String fPath = new File(f.getAbsolutePath()).getAbsolutePath();
                    String hPath = new File(gh.getGraphHopperStorage().getDirectory().getLocation()).getAbsolutePath();
                    if (fPath.equals(hPath))
                        continue fileloop;
                }
            }
            try {
                hopper = GHAsyncLoader.loadGraphhopperStorage(f.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                continue;
            }
            if (isInGraphHopper(hopper, pt)) {
                ghMemory.add(hopper);
                break;
            }
        }
        //lockSelectGraphhoper.unlock();
        Log.w("Hopper", "Unlock" + hopper.getGraphHopperLocation());
        if (!hopper.equals(lastCalledGraphHopper))
            lastCalledGraphHopper = hopper;
        return hopper;
    }

    public synchronized static boolean isInGraphHopper(GraphHopper hopper, GHPoint pt) {
        if (hopper != null) {
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
        return mGhPoint;
    }

    public void setGhPoint(GHPoint mGhPoint) {
        this.mGhPoint = mGhPoint;
    }
}
