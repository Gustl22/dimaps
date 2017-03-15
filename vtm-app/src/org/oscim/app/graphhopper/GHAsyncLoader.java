package org.oscim.app.graphhopper;

import android.util.Log;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.util.Constants;

public class GHAsyncLoader extends GHAsyncTask<String, Void, GraphHopper>{

    public AsyncGraphhopperResponse delegate = null;

    public GHAsyncLoader(AsyncGraphhopperResponse delegate){
        super();
        Log.d("Load", "loading graph (" + Constants.VERSION + ") ... ");
        this.delegate = delegate;
    }

    @Override
    protected GraphHopper saveDoInBackground(String[] params) throws Exception {
        return loadGraphhopperStorage(params[0]);
    }

    protected void onPostExecute(GraphHopper gh) {
        if (hasError()) {
            Log.e("Error", "An error happened while creating graph:"
                    + getErrorMessage());
        } else {
            Log.d("", "Finished loading graph. Press long to define where to start and end the route.");
        }
        delegate.processFinish(gh);
    }

    public static GraphHopper loadGraphhopperStorage(String path) throws IllegalAccessException{
        GraphHopper tmpHopp = new GraphHopperOSM().forMobile();
//                String db = getDbMapName();
//                tmpHopp.setDataReaderFile(storage + "/" + db);
//                tmpHopp.setGraphHopperLocation(storage);
//                tmpHopp.setEncodingManager(new EncodingManager("car")); //car, bike or ...
//                tmpHopp.importOrLoad();

        if(!tmpHopp.load(path)){
            throw new IllegalAccessException("Graphhopper filepath does not exist");
        }
        Log.d("Found", "found graph " + tmpHopp.getGraphHopperStorage().toString() + ", nodes:" + tmpHopp.getGraphHopperStorage().getNodes());
        return tmpHopp;
    }
}

