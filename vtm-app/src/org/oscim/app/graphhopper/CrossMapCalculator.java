package org.oscim.app.graphhopper;

import android.app.ProgressDialog;
import android.util.Log;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.app.ProgressDialog.STYLE_HORIZONTAL;

public class CrossMapCalculator {

    public CrossMapCalculator(CrossMapCalculatorListener listener){
        addListener(listener);
    }
    private List<CrossMapCalculatorListener> listeners = new ArrayList<CrossMapCalculatorListener>();

    public void addListener(CrossMapCalculatorListener toAdd) {
        listeners.add(toAdd);
    }
    public void updateCrossMapCalculatorListener(String status, int progress, int ProgressDialogStyle) {
        // Notify everybody that may be interested.
        for (CrossMapCalculatorListener hl : listeners)
            hl.onCrossMapCalculatorUpdate(status, progress, ProgressDialogStyle);
    }

    /**
     * Calculates the crossPoint on base of GHIndex. Bit lacky, but correct.
     * @param a1 first Point inclusive its GraphHopper Map
     * @param a2 second Point inclusive its GraphHopper Map
     * @return CrossPoint of best route between two areas
     */
    public GHPoint getCrossPoint(GHPointArea a1, GHPointArea a2) {
        GraphHopper g1 = a1.getGraphHopper();
        GraphHopper g2 = a2.getGraphHopper();
        //TODO Return maps with calculated subroutes
//        HashSet<Integer> firstNodes = new HashSet<Integer>();
//        HashSet<Integer> scndNodes = new HashSet<Integer>();
        Graph gr1 = g1.getGraphHopperStorage().getBaseGraph();
        Graph gr2 = g2.getGraphHopperStorage().getBaseGraph();
        NodeAccess na1 = gr1.getNodeAccess();
        NodeAccess na2 = gr2.getNodeAccess();
//        AllEdgesIterator firstEdges = gr1.getAllEdges();
//        AllEdgesIterator scndEdges = gr2.getAllEdges();
        LocationIndex l1 = g1.getLocationIndex();
        LocationIndex l2 = g2.getLocationIndex();

        //Calculation of nearest point
        double lat1 = a1.getGhPoint().getLat();
        double lat2 = a2.getGhPoint().getLat();
        double lon1 = a1.getGhPoint().getLon();
        double lon2 = a2.getGhPoint().getLon();

        GHPoint flyPoint = new GHPoint(lat1, lon1);

        HashMap<Integer, Integer> pointTable = loadBoundNodes(g1, g2);
        int progress = 0;
        int counter = 0;
        int max = pointTable.size();
        updateCrossMapCalculatorListener("Calculate route across areas",
                0, ProgressDialog.STYLE_HORIZONTAL);
        double smallestRouteWeight = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : pointTable.entrySet()) {
            Integer key1 = entry.getKey();
            Integer key2 = entry.getValue();

            GHRequest req1 = new GHRequest(lat1, lon1, na1.getLat(key1), na1.getLon(key1)).
                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
            req1.getHints().put(Parameters.Routing.INSTRUCTIONS, "false");
            GHResponse resp1 = a1.getGraphHopper().route(req1);

            GHRequest req2 = new GHRequest(na2.getLat(key2), na2.getLon(key2), lat2, lon2).
                    setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
            req2.getHints().put(Parameters.Routing.INSTRUCTIONS, "false");
            GHResponse resp2 = a2.getGraphHopper().route(req2);

            if (resp1.hasErrors() || resp2.hasErrors()) continue;
            double actRw = resp1.getBest().getRouteWeight() + resp2.getBest().getRouteWeight();
            if (actRw < smallestRouteWeight) {
                smallestRouteWeight = actRw;
                flyPoint = new GHPoint(na2.getLat(key2), na2.getLon(key2));
            }
            counter += 1;
            int newProgress = Math.round((counter / (float) max) * 100);
            if (newProgress != progress) {
                progress = newProgress;
                updateCrossMapCalculatorListener("Calculate route across areas.",
                        progress, ProgressDialog.STYLE_HORIZONTAL);
            }
        }
        updateCrossMapCalculatorListener("Finish cross-routing",
                100, ProgressDialog.STYLE_HORIZONTAL);
        return flyPoint;

    }

    public double getDistance(GHPoint p1, GHPoint p2) {
        double Lat = p1.getLat() - p2.getLat();
        double Lon = p1.getLon() - p2.getLon();
        return Math.sqrt(Lat * Lat + Lon * Lon);
    }

    /**
     * Approaches a common point of two areas, if index is not calculated.
     * @param p1 Point of first area
     * @param p2 Point of second area
     * @param l1 LocationIndex of first area
     * @param l2 LocatioinIndex of second area
     * @return both NodeIds of first and second area, which have the same coordinates.
     */
    public int[] approachCrossPoint(GHPoint p1, GHPoint p2, LocationIndex l1, LocationIndex l2) {
        double lat1 = p1.getLat();
        double lat2 = p2.getLat();
        double lon1 = p1.getLon();
        double lon2 = p2.getLon();
        double Lat = lat1 - lat2;
        double Lon = lon1 - lon2;
        double midLat = (Lat) / 2 + lat2;
        double midLon = (Lon) / 2 + lon2;
        GHPoint mid = new GHPoint(midLat, midLon);

        QueryResult qr1 = l1.findClosest(midLat, midLon, EdgeFilter.ALL_EDGES);
        QueryResult qr2 = l2.findClosest(midLat, midLon, EdgeFilter.ALL_EDGES);
        boolean firstSet = qr1.getClosestNode() > 0;
        if (qr2.getClosestNode() > 0) {
            if (firstSet) {
                return new int[]{qr1.getClosestNode(), qr2.getClosestNode()};
            } else {
                return approachCrossPoint(p1, mid, l1, l2);
            }
        } else {
            if (firstSet) {
                return approachCrossPoint(mid, p2, l1, l2);
            }
            return new int[]{-1, -1};
        }
    }

    /**
     * Calculates all nodes between two areas
     * @param gh1 first graphhopper (with storage)
     * @param gh2 second graphhopper (with storage)
     * @return Hashmap of NodeIDs (key: first graph, value: sec graph)
     */
    public HashMap<Integer, Integer> getAllBoundNodes(GraphHopper gh1, GraphHopper gh2) {
        boolean switchGH = false;
        updateCrossMapCalculatorListener("Init calculation...", 0, ProgressDialog.STYLE_HORIZONTAL);
        if (gh1.getGraphHopperStorage().getNodes() > gh2.getGraphHopperStorage().getNodes()) {
            GraphHopper tmp = gh1;
            gh1 = gh2;
            gh2 = tmp;
            switchGH = true;
        }
        GraphHopperStorage ghSt1 = gh1.getGraphHopperStorage();
        GraphHopperStorage ghSt2 = gh2.getGraphHopperStorage();
        Graph graph1 = ghSt1.getBaseGraph();
        Graph graph2 = ghSt2.getBaseGraph();
        NodeAccess na1 = graph1.getNodeAccess();
        NodeAccess na2 = graph2.getNodeAccess();
        LocationIndex lIndex2 = gh2.getLocationIndex();
        HashMap<Integer, Integer> nodes = new HashMap<>();
        AllEdgesIterator edge = graph1.getAllEdges();
        int progress = 0;
        int max = edge.getMaxId();
        float counter = 0;
        while (edge.next()) {
            counter += 1;
            int nodeB = edge.getBaseNode();
            int nodeA = edge.getAdjNode();
            double latB = na1.getLatitude(nodeB);
            double lonB = na1.getLongitude(nodeB);
            double latA = na1.getLatitude(nodeA);
            double lonA = na1.getLongitude(nodeA);

            int nodeB2 = lIndex2.findClosest(latB, lonB, EdgeFilter.ALL_EDGES).getClosestNode();
            int nodeA2 = lIndex2.findClosest(latA, lonA, EdgeFilter.ALL_EDGES).getClosestNode();
            if (nodeB2 > 0) {
                if (na2.getLatitude(nodeB2) == latB && na2.getLongitude(nodeB2) == lonB) {
                    nodes.put(nodeB, nodeB2);
                }
            }
            if (nodeA2 > 0) {
                if (na2.getLatitude(nodeA2) == latA && na2.getLongitude(nodeA2) == lonA) {
                    nodes.put(nodeA, nodeA2);
                }
            }
            int newProgress = Math.round((counter/(float) max)*100);
            if(newProgress != progress){
                progress = newProgress;
                updateCrossMapCalculatorListener("Calculate match points...", progress,
                        STYLE_HORIZONTAL);
            }
        }
        updateCrossMapCalculatorListener("Calculation ready", 100, STYLE_HORIZONTAL);
        if(switchGH){
            return switchHashMap(nodes);
        }
        return nodes;
    }

    /**
     * Loads bound nodes of crossing areas from storage, or creates them if not existant.
     * One of the both storages then contains a HashMap List.
     * @param gh1 first graphhopper (with storage)
     * @param gh2 first graphhopper (with storage)
     * @return Hashmap of NodeIDs (key: first graph, value: sec graph)
     */
    public HashMap<Integer, Integer> loadBoundNodes(GraphHopper gh1, GraphHopper gh2) {
        updateCrossMapCalculatorListener("Load match points", 0, ProgressDialog.STYLE_SPINNER);
        String location1 = gh1.getGraphHopperLocation();
        String location2 = gh2.getGraphHopperLocation();
        String fileName1 = location2.substring(location2.lastIndexOf("/") + 1) + ".hash";
        String fileName2 = location1.substring(location2.lastIndexOf("/") + 1) + ".hash";
        File file1 = new File(location1, fileName1);
        File file2 = new File(location2, fileName2);
        if (file1.exists() || file2.exists()) {
            String filePath;
            boolean isSecFile = false;
            if (file1.exists()) {
                filePath = file1.getAbsolutePath();
            } else {
                isSecFile = true;
                filePath = file1.getAbsolutePath();
            }
            try {
                FileInputStream fileInputStream = new FileInputStream(filePath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

                HashMap<Integer, Integer> pointMap =
                        (HashMap<Integer, Integer>) objectInputStream.readObject();
                objectInputStream.close();
                if (isSecFile) {
                    pointMap = switchHashMap(pointMap);
                }
                updateCrossMapCalculatorListener("Match points loaded", 100, 0);
                return pointMap;
            } catch (IOException | ClassNotFoundException ex) {
                Log.w("Exception Hashpmap File", ex.getMessage());
            }
            updateCrossMapCalculatorListener("No Match points found", 100, 0);
            return null;
        } else {
            HashMap<Integer, Integer> hm = getAllBoundNodes(gh1, gh2);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file1);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

                objectOutputStream.writeObject(hm);
                objectOutputStream.close();
            } catch (IOException ex) {
                Log.w("Exception Hashpmap File", ex.getMessage());
            }
            updateCrossMapCalculatorListener("Match points loaded", 100, 0);
            return hm;
        }
    }

    public HashMap<Integer, Integer> switchHashMap(HashMap<Integer, Integer> hashmap){
        HashMap<Integer, Integer> hm = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, Integer> entry : hashmap.entrySet()) {
            hm.put(entry.getValue(), entry.getKey());
        }
        return hm;
    }
}
