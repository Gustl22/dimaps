package org.oscim.app.graphhopper;

import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by gustl on 22.03.17.
 */

public class GHPointAreaRoute {
    private Collection<GHPointListener> mListeners = new HashSet<GHPointListener>();

    public void addListener(GHPointListener toAdd) {
        mListeners.add(toAdd);
    }
    public void updateRoutePoints() {
        // Notify everybody that may be interested.
        for (GHPointListener hl : mListeners)
            hl.onRoutePointUpdate();
    }

    private static GHPointAreaRoute mGHPointAreaRoute;
    //Route expressed in GHPointAreas.
    private static Collection<GHPointArea> GHPointAreas;

    private GHPointAreaRoute(){
        GHPointAreas = new HashSet<GHPointArea>();
    }

    public Collection<GHPointArea> getGHPointAreas(){
        return GHPointAreas;
    }

    public void setGHPointAreas(Collection<GHPointArea> ghPointAreas){
        GHPointAreas = ghPointAreas;
    }

    public static GHPointAreaRoute getInstance(){
        if(mGHPointAreaRoute == null){
            mGHPointAreaRoute = new GHPointAreaRoute();
        }
        return mGHPointAreaRoute;
    }

    /**
     * @param ghPointArea If the GHPointArea you override was not null, else type null
     *                   (Background: the point you override must deleted from the internal list,
     *                   so it does not disturb)
     * @param rpl Listener that gets informed if route changes
     */
    public void add(GHPointArea ghPointArea, GHPointListener rpl){
        AsyncTask<Object, Void, Void> task = new AsyncTask<Object, Void, Void>(){
            @Override
            protected Void doInBackground(Object[] params) {
                GHPointArea tempPointArea = (GHPointArea) params[0];
                if(tempPointArea.getGraphHopper() == null){
                    synchronized(tempPointArea.virtualObject) {
                        try {
                            // Calling wait() will block this thread until another thread
                            // calls notify() on the object.
                            tempPointArea.virtualObject.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                GHPointAreas.add(tempPointArea);
                addListener((GHPointListener) params[1]);
                updateRoutePoints();
                return null;
            }
        };
        AsyncTaskCompat.executeParallel(task, ghPointArea, rpl );
    }

    public void remove(GHPointArea ghPointArea){
        if(ghPointArea != null){
            getGHPointAreas().remove(ghPointArea);
        }
        //updatePoints(); //Is not enabled because this will cause overhead
    }
}
