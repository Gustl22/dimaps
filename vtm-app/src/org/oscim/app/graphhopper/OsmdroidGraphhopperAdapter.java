package org.oscim.app.graphhopper;

import com.graphhopper.PathWrapper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import org.oscim.core.GeoPoint;
import org.osmdroid.routing.Route;

import java.util.List;

public class OsmdroidGraphhopperAdapter {
    public static PathWrapper convertRouteToPathWrapper(Route Route) {
        PathWrapper PathWrapper = new PathWrapper();
        PathWrapper.setPoints(convertGeoPointsToPointList(Route.getRouteLow()));
        PathWrapper.setTime(((long) Route.duration) * 1000);
        PathWrapper.setDistance(Route.length * 1000);
        //... May should be extended

        return PathWrapper;
    }

    public static PointList convertGeoPointsToPointList(List<GeoPoint> geoPoints) {
        PointList pointList = new PointList();
        for (int i = 0; i < geoPoints.size(); i++) {
            pointList.add(geoPoints.get(i).getLatitude(), geoPoints.get(i).getLongitude());
        }
        return pointList;
    }

    public static GHPoint convertGeoPointToGHPoint(GeoPoint geoPoint){
        return new GHPoint(geoPoint.getLatitude(), geoPoint.getLongitude());
    }
}
