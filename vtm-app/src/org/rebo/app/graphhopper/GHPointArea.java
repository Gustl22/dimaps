package org.rebo.app.graphhopper;

import android.util.Log;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import org.rebo.app.App;

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
        new GHNotifyTask().execute(ghPoint, ghFiles, sGraphHopperMemory);
    }

    private class GHNotifyTask extends GHAsyncTask<Object, Void, GraphHopper> {
        @Override
        protected GraphHopper saveDoInBackground(Object... params) throws Exception {
            return autoSelectGraphhopper((GHPoint) params[0], (List<File>) params[1],
                    (Collection<GraphHopper>) params[2]);
        }

        protected void onPostExecute(GraphHopper hopper) {
            graphHopper = hopper;
            synchronized (virtualObject) {
                virtualObject.notifyAll();
            }
            if (hopper == null) {
                App.activity.showToastOnUiThread("No Hopper matches the point");
            }
        }
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

        //Log.w("Hopper", "Lock");
        if (lastCalledGraphHopper != null) {
            if (isInGraphHopper(lastCalledGraphHopper, pt)) {
//                App.activity.showToastOnUiThread("LastCalledHopper: "
//                        + lastCalledGraphHopper.getGraphHopperStorage());
                return lastCalledGraphHopper;
            }
        }
        //lockSelectGraphhoper.lock();
        //Search Point in already loaded areas
        for (GraphHopper gh : sGraphHopperMemory) {
            if (isInGraphHopper(gh, pt)) {
                if (!gh.equals(lastCalledGraphHopper))
                    lastCalledGraphHopper = gh;
//                App.activity.showToastOnUiThread("Graphhopper from existent: "
//                        + gh.getGraphHopperStorage());
                return gh;
            }
        }
        GraphHopper hopper = null;
        //App.activity.showToastOnUiThread("GHFileCount: "+ghFiles.size());
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
                //Storage not exists or has wrong type
                //ex.printStackTrace();
                continue;
            }
            if (isInGraphHopper(hopper, pt)) {
                ghMemory.add(hopper);
//                App.activity.showToastOnUiThread("Hopper was new loaded: "
//                        + hopper.getGraphHopperStorage());
                break;
            }
        }
        //lockSelectGraphhoper.unlock();
        //Log.w("Hopper", "Unlock" + hopper.getGraphHopperLocation());
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
