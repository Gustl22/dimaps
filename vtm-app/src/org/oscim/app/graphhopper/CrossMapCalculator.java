package org.oscim.app.graphhopper;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by gustl on 13.03.17.
 */

public class CrossMapCalculator {
    GraphHopper mainHopper;
    GraphHopper mScndHopper;
    GHRequest ghRequest;
    List<GHPoint> ghPointList;
    /*
    Recalculates route, if it extends over multiple areas.
     */
//    public GHResponse recalculateRoute(final GHResponse ghResponse, List<File> ghFiles){
//        final String pathError = "Cannot find point ";
//        ghPointList = ghRequest.getPoints();
//
//        for(File f: ghFiles){
//            if(!f.getAbsolutePath().equals(mainHopper.getGraphHopperLocation())){
//
//            new GHAsyncLoader(new AsyncGraphhopperResponse(){
//                @Override
//                public void processFinish(GraphHopper graphHopper) {
//                    mScndHopper = graphHopper;
//                    for(Throwable err : ghResponse.getErrors()){
//                        String msg = err.getMessage();
//                        if(msg.contains(pathError)){
//                            int ptIndex = Integer.parseInt(msg.substring(pathError.length(), msg.indexOf(":")));
//                            GHPoint pt = ghPointList.get(ptIndex);
//                            ArrayList<GHPoint> arr = new ArrayList<>();
//                            arr.add(pt); arr.add(pt);
//                            GHRequest request = new GHRequest(arr).
//                                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
//                            request.getHints().
//                                    put(Parameters.Routing.INSTRUCTIONS, "false");
//                            GHResponse resp = mScndHopper.route(request);
//                            if(!resp.getErrors().isEmpty()){
//                                continue;
//                            }
//
//                        }
//                    }
//                }
//            }).execute(ghFiles.get(0).getAbsolutePath());
//
//            }
//        }
//        return null;
//    }

    public static GHPoint getCrossPoint(GHPointArea a1, GHPointArea a2){
        GraphHopper g1 = a1.getGraphHopper();
        GraphHopper g2 = a2.getGraphHopper();
        //TODO Return maps with calculated subroutes
        HashSet<Integer> firstNodes = new HashSet<Integer>();
        HashSet<Integer> scndNodes = new HashSet<Integer>();
        Graph gr1 = g1.getGraphHopperStorage().getBaseGraph();
        Graph gr2 = g2.getGraphHopperStorage().getBaseGraph();
        NodeAccess na1 = gr1.getNodeAccess();
        //NodeAccess na2 = gr2.getNodeAccess();
        AllEdgesIterator firstEdges = gr1.getAllEdges();
        AllEdgesIterator scndEdges = g2.getGraphHopperStorage().getBaseGraph().getAllEdges();
        //LocationIndex l1 = g1.getLocationIndex();
        //LocationIndex l2 = g2.getLocationIndex();
        do{
            firstNodes.add(firstEdges.getBaseNode());
        } while (firstEdges.next());
        do{
            scndNodes.add(scndEdges.getBaseNode());
        } while (scndEdges.next());
        firstNodes.retainAll(scndNodes);

        //Remove unnecessary nodes
        double lat1 = a1.getGhPoint().getLat();
        double lat2 = a2.getGhPoint().getLat();
        double lon1 = a1.getGhPoint().getLon();
        double lon2 = a1.getGhPoint().getLat();
        double Lat = lat1-lat2;
        double Lon = lon1-lon2;
        double midLat = (Lat)/2 + lat2;
        double midLon = (Lon)/2 + lon2;
        double diameter = Math.sqrt(Lat*Lat + Lon*Lon);

        List<GHPoint> possiblePoints = new ArrayList<>();
        for(Integer pt : firstNodes){
            GHPoint ghPoint = new GHPoint(na1.getLat(pt), na1.getLon(pt));
            if(Math.pow(ghPoint.getLat() - midLat, 2) + Math.pow(ghPoint.getLon() - midLon,2)
                    < Math.pow(diameter/2,2))
                possiblePoints.add(ghPoint);
        }

        GHPoint ShortestPoint = possiblePoints.get(0);
        long shortestTime = Long.MAX_VALUE;
        for(GHPoint ghPoint : possiblePoints){
            List<GHPoint> tmpRoute = new ArrayList<>();
            tmpRoute.add(a1.getGhPoint());
            tmpRoute.add(ghPoint);
            GHRequest req = new GHRequest(tmpRoute).
                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
            req.getHints().
                    put(Parameters.Routing.INSTRUCTIONS, "false");
            GHResponse resp = a1.getGraphHopper().route(req);

            List<GHPoint> tmpRoute2 = new ArrayList<>();
            tmpRoute.add(a2.getGhPoint());
            tmpRoute.add(ghPoint);
            GHRequest req2 = new GHRequest(tmpRoute).
                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
            req.getHints().
                    put(Parameters.Routing.INSTRUCTIONS, "false");
            GHResponse resp2 = a2.getGraphHopper().route(req);
            long time = resp.getBest().getTime() + resp2.getBest().getTime();
            if(time < shortestTime){
                shortestTime = time;
                ShortestPoint = ghPoint;
            }
        }
        return ShortestPoint;
    }
}
