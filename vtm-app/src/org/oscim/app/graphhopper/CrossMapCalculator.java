package org.oscim.app.graphhopper;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
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

    public CrossMapCalculator(GraphHopper mainGraph, GHRequest ghRequest){
        this.ghRequest = ghRequest;
        this.mainHopper = mainGraph;
    }
    /*
    Recalculates route, if it extends over multiple areas.
     */
    public GHResponse recalculateRoute(final GHResponse ghResponse, List<File> ghFiles){
        final String pathError = "Cannot find point ";
        ghPointList = ghRequest.getPoints();

        for(File f: ghFiles){
            if(!f.getAbsolutePath().equals(mainHopper.getGraphHopperLocation())){

            new GHAsyncLoader(new AsyncGraphhopperResponse(){
                @Override
                public void processFinish(GraphHopper graphHopper) {
                    mScndHopper = graphHopper;
                    for(Throwable err : ghResponse.getErrors()){
                        String msg = err.getMessage();
                        if(msg.contains(pathError)){
                            int ptIndex = Integer.parseInt(msg.substring(pathError.length(), msg.indexOf(":")));
                            GHPoint pt = ghPointList.get(ptIndex);
                            ArrayList<GHPoint> arr = new ArrayList<>();
                            arr.add(pt); arr.add(pt);
                            GHRequest request = new GHRequest(arr).
                                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
                            request.getHints().
                                    put(Parameters.Routing.INSTRUCTIONS, "false");
                            GHResponse resp = mScndHopper.route(request);
                            if(!resp.getErrors().isEmpty()){
                                continue;
                            }
                            //TODO Return maps with calculated subroutes
                            HashSet<Integer> firstNodes = new HashSet<Integer>();
                            HashSet<Integer> scndNodes = new HashSet<Integer>();
                            AllEdgesIterator firstEdges = mainHopper.getGraphHopperStorage().getBaseGraph().getAllEdges();
                            AllEdgesIterator scndEdges = mScndHopper.getGraphHopperStorage().getBaseGraph().getAllEdges();
                            do{
                                firstNodes.add(firstEdges.getBaseNode());
                            } while (firstEdges.next());
                            do{
                                scndNodes.add(scndEdges.getBaseNode());
                            } while (scndEdges.next());
                            firstNodes.retainAll(scndNodes);
                            break;
                        }
                    }
                }
            }).execute(ghFiles.get(0).getAbsolutePath());

            }
        }
        return null;
    }
}
