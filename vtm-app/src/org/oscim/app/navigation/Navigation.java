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

import java.util.Iterator;

/**
 * Copyright by Gustl22 on 05.04.17.
 *
 */

public class Navigation implements LocationListener {
    private PathWrapper pathWrapper;
    private Instruction mLastInstruction;
//    private List<Path> pathList;
//    private int currentRouteProgressPoint = -1;

    public Navigation(PathWrapper pathWrapper) {
        this.pathWrapper = pathWrapper;
    }

    public PathWrapper calculateCurrentPath(Location snapLocation) {
        GHPoint ghPoint = new GHPoint(snapLocation.getLatitude(), snapLocation.getLongitude());
        PointList points = pathWrapper.getPoints();
        Iterator<GHPoint3D> pointIterator = points.iterator();

        int i = -1;
        boolean pointOccured = false;
        while (pointIterator.hasNext()) {
            GHPoint pt = pointIterator.next();
            if (ghPoint.equals(pt)) {
                pointOccured = true;
                break;
            } else {
                i++;
            }
        }

        //TODO Replace waypoints with getpoints or iterate instructions!

        InstructionList instructions = pathWrapper.getInstructions();
        Iterator<Instruction> iterator = instructions.iterator();

        int j = i + 1;
        boolean meetCurrent = false;
        Instruction instruction = null;
        while (iterator.hasNext()) {
            instruction = iterator.next();
            if (!meetCurrent && instruction.equals(mLastInstruction)) {
                meetCurrent = true;
            }
            if (meetCurrent || mLastInstruction == null) {
                PointList pointsI = instruction.getPoints();
                if (j >= pointsI.getSize()) {
                    j -= pointsI.getSize();
                } else {
                    break;
                }
                mLastInstruction = instruction;
            }
        }
        if (!pointOccured) {
            //TODO calc the path again with graphhopper
            App.activity.showToastOnUiThread("Distance too big. Calculate again.");
            App.routeSearch.setStartPoint(new GHPointArea(
                    new GHPoint(snapLocation.getLatitude(), snapLocation.getLongitude()),
                    App.routeSearch.getGraphHopperFiles()));
            return pathWrapper;
        }

//        GHPointArea ghPointArea = new GHPointArea(ghPoint, RouteSearch.getGraphHopperFiles());
//        GraphHopper graphHopper = ghPointArea.getGraphHopper();
//        NodeAccess na = graphHopper.getGraphHopperStorage().getNodeAccess();
//        Graph graph = graphHopper.getGraphHopperStorage().getBaseGraph();
//        EdgeExplorer explorer = graph.createEdgeExplorer(EdgeFilter.ALL_EDGES);

        points = points.copy(i, points.getSize() - 1);
        if (instruction == null || instructions.isEmpty()) return pathWrapper;
        double currentDistance = pathWrapper.getDistance() - instruction.getDistance();
        long currentTime = pathWrapper.getTime() - instruction.getTime();


//        int last = -1;
//        for (Path path : pathList) {
//            path.setFound(false);
//            TIntList tIntList = path.calcNodes();
//            TIntIterator iterator = tIntList.iterator();
//            boolean meetCurrent = false;
//            while(iterator.hasNext()){
//                int next = iterator.next();
//                if(currentRouteProgressPoint < 0) currentRouteProgressPoint = next;
//                if(!meetCurrent && next == currentRouteProgressPoint) {
//                    meetCurrent = true;
//                }
//                if(meetCurrent){
//                    GHPoint pathPoint = new GHPoint(na.getLatitude(next), na.getLongitude(next));
//                    if(pathPoint.equals(nearestPoint)){
//                        EdgeIterator iter = explorer.setBaseNode(last);
//                        while (iter.next()) {
//                            if(iter.getAdjNode() == next){
//                                iter.getEdge();
//                            }
//
//                            double tmpLat = na.getLatitude(iter.getAdjNode());
//                            double tmpLon = na.getLongitude(iter.getAdjNode());
//                            float preAngle = (float) Math.atan2(diffLat, diffLon);
//                            float tmpAngle = (float) Math.atan2(curLat - tmpLat, curLon - tmpLon);
//                            if (Math.abs((tmpAngle - preAngle + Math.PI) % (2 * Math.PI)) < Math.PI / 2) {
//                                nextNode = iter.getAdjNode();
//                                break;
//                            }
//                        }
//                    }
//                }
//                last = next;
//            }
//        }

        PathWrapper currentWrapper = new PathWrapper();
        currentWrapper.setWaypoints(pathWrapper.getWaypoints());
        currentWrapper
                .setDistance(currentDistance)
                .setPoints(points)
                .setTime(currentTime);
//        App.activity.showToastOnUiThread("Pathwrapper set");
        return currentWrapper;
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
