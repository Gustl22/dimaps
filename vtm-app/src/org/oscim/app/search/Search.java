package org.oscim.app.search;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;

/**
 * Created by gustl on 17.03.17.
 */

public class Search {
    public void loadStreetNames(Graph graph){
        EdgeIterator iter = graph.getAllEdges();
        while(iter.next()) {
            iter.getEdge();
            PointList points = iter.fetchWayGeometry(2);
            String name = iter.getName();
        }
    }
}
