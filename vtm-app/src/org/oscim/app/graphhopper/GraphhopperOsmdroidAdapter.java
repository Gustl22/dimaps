package org.oscim.app.graphhopper;

import com.graphhopper.PathWrapper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import org.oscim.core.GeoPoint;
import org.osmdroid.routing.Route;

import java.util.ArrayList;

/**
 * Created by gustl on 15.03.17.
 */

public class GraphhopperOsmdroidAdapter {
    public static Route convertPathWrapperToRoute(PathWrapper PathWrapper) {
        Route route = new Route();
        route.routeHigh = convertPointListToGeoPoints(PathWrapper.getPoints());
        route.setRouteLow(route.routeHigh);
        route.duration = PathWrapper.getTime() / 1000;
        route.length = PathWrapper.getDistance() / 1000;
        //... May should be extended

        return route;
    }

    public static ArrayList<GeoPoint> convertPointListToGeoPoints(PointList pointList) {
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();
        for (int i = 0; i < pointList.getSize(); i++) {
            geoPoints.add(new GeoPoint(pointList.getLatitude(i), pointList.getLongitude(i)));
        }
        return geoPoints;
    }

    public static GeoPoint convertGHPointToGeoPoint(GHPoint ghPoint){
        return new GeoPoint(ghPoint.getLat(), ghPoint.getLon());
    }
}

