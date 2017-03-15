package org.oscim.app.graphhopper;

import com.graphhopper.GraphHopper;

public interface AsyncGraphhopperResponse {
    void processFinish(GraphHopper graphHopper);
}
