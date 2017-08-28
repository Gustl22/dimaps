package org.oscim.app.navigation;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import com.graphhopper.PathWrapper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import org.oscim.app.App;
import org.oscim.app.graphhopper.GHPointArea;
import org.oscim.app.location.LocationHandler;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeoPoint;

import java.util.Iterator;

/**
 * Copyright by Gustl22 on 05.04.17.
 */

public class Navigation implements LocationListener {
    private PathWrapper pathWrapper;
    private Instruction lastInstruction;
//    private List<Path> pathList;
//    private int currentRouteProgressPoint = -1;

    public Navigation(PathWrapper pathWrapper) {
        this.pathWrapper = pathWrapper;
    }

    public PathWrapper calculateCurrentPath(Location snapLocation) {
        GHPoint ghPoint = new GHPoint(snapLocation.getLatitude(), snapLocation.getLongitude());
        PointList points = pathWrapper.getPoints();
        PointList waypoints = pathWrapper.getWaypoints();
        Iterator<GHPoint3D> pointIterator = points.iterator();
        Iterator<GHPoint3D> wayPointIterator = waypoints.iterator();
        GHPoint wayPoint = null;

        int i = -1;
        boolean pointOccured = false;
        while (pointIterator.hasNext()) {
            GHPoint pt = pointIterator.next();
            //Handle Waypoints
            if (wayPoint == null || wayPoint.equals(pt)) {
                if (wayPointIterator.hasNext())
                    wayPoint = wayPointIterator.next();
            }
            //Handle pathpoints
            if (ghPoint.equals(pt)) {
                pointOccured = true;
                break;
            } else {
                i++;
            }
        }

        if (!pointOccured) {
            App.activity.showToastOnUiThread("Distance too big. Calculate again.");
            App.routeSearch.setStartPoint(new GHPointArea(
                    new GHPoint(snapLocation.getLatitude(), snapLocation.getLongitude()),
                    App.routeSearch.getGraphHopperFiles()));
            return null;
        }

        InstructionList instructions = pathWrapper.getInstructions();
        Iterator<Instruction> iterator = instructions.iterator();

        int j = i + 1;
        boolean meetCurrent = false;
        Instruction instruction = null;
        while (iterator.hasNext()) {
            instruction = iterator.next();
            if (!meetCurrent && instruction.equals(lastInstruction)) {
                meetCurrent = true;
            }
            if (meetCurrent || lastInstruction == null) {
                PointList pointsI = instruction.getPoints();
                lastInstruction = instruction;
                if (j >= pointsI.getSize()) {
                    j -= pointsI.getSize();
                } else {
                    break;
                }
            }
        }

        points = points.copy(i < 0 ? 0 : i, points.getSize());
        if (points.size() < 3) {
            //If Route ends, finish nav mode;
            App.activity.toggleLocation(LocationHandler.Mode.SNAP);
        }

        if (instruction == null || instructions.isEmpty()) return pathWrapper;
        double currentDistance = pathWrapper.getDistance() - lastInstruction.getDistance();
        long currentTime = pathWrapper.getTime() - lastInstruction.getTime();
        PointList wayPointList = new PointList();
        wayPointList.add(wayPoint);
        while (wayPointIterator.hasNext()) {
            wayPointList.add(wayPointIterator.next());
        }

        PathWrapper currentWrapper = new PathWrapper();
        currentWrapper
                .setDistance(currentDistance)
                .setPoints(points)
                .setTime(currentTime)
                .setWaypoints(wayPointList);
//        App.activity.showToastOnUiThread("Pathwrapper set");
        return currentWrapper;
    }

    public Instruction getLastInstruction() {
        return lastInstruction;
    }

    public BoundingBox getRouteBounds() {
        PointList pl = pathWrapper.getWaypoints();
        Iterator<GHPoint3D> iterator = pl.iterator();
        BoundingBox bbox = null;
        while (iterator.hasNext()) {
            GHPoint3D ghp = iterator.next();
            if (bbox == null) {
                bbox = new BoundingBox(ghp.getLat(), ghp.getLon(),
                        ghp.getLat(), ghp.getLon());
            } else {
                bbox = bbox.extendCoordinates(new GeoPoint(ghp.getLat(), ghp.getLon()));
            }
        }
        return bbox;
    }

    @Override
    public void onLocationChanged(Location snapLocation) {
        //Update pathwrapper in routesearch
        App.routeSearch.updateOverlays(calculateCurrentPath(snapLocation));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
