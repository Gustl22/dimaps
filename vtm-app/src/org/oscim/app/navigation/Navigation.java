package org.oscim.app.navigation;

import android.location.Location;

import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import org.oscim.app.RouteSearch;
import org.oscim.app.graphhopper.GHPointArea;

import java.util.Iterator;

import static org.oscim.app.graphhopper.GraphhopperOsmdroidAdapter.convertGHPointToGeoPoint;

/**
 * Created by gustl on 05.04.17.
 */

public class Navigation {
    private PathWrapper pathWrapper;

    public Navigation(PathWrapper pathWrapper) {
        this.pathWrapper = pathWrapper;
    }

    public PathWrapper calculateCurrentPath(Location location) {
        GHPoint ghPoint = new GHPoint(location.getLatitude(), location.getLongitude());
        GHPointArea ghPointArea = new GHPointArea(ghPoint, RouteSearch.getGraphHopperFiles());
        GraphHopper graphHopper = ghPointArea.getGraphHopper();

//        LocationIndex li = graphHopper.getLocationIndex();
//        li.findClosest(location.getLatitude(), location.getLongitude(), EdgeFilter.ALL_EDGES);
        double distance = Double.MAX_VALUE;
        PointList waypoints = pathWrapper.getWaypoints();
        Iterator<GHPoint3D> it = waypoints.iterator();
        int i = 0;
        while (it.hasNext()) {
            GHPoint3D point = (GHPoint3D) it.next();
            double actDis = convertGHPointToGeoPoint(point)
                    .sphericalDistance(convertGHPointToGeoPoint(ghPoint)); //in meters
            if (actDis < distance) {
                distance = actDis;
            } else {
                break;
            }
            i++;
        }
        waypoints = waypoints.copy(i - 1, waypoints.getSize() - 1);

        PathWrapper currentWrapper = new PathWrapper();
        currentWrapper.setWaypoints(waypoints);
        return currentWrapper;
    }
}
