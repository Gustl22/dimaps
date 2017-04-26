package org.oscim.app.location;

import android.util.Log;

import org.mapsforge.core.model.LatLong;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 26.04.17.
 */

public class LocationPersistenceManager {

    public static List<LatLong> fetchLocations(File destination) {
        if (destination.exists()) {
            String filePath;
            filePath = destination.getAbsolutePath();

            try {
                FileInputStream fileInputStream = new FileInputStream(filePath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

                List<Double[]> favorMap = (List<Double[]>) objectInputStream.readObject();
                objectInputStream.close();
                List<LatLong> latlongs = new ArrayList<>();
                for (Double[] doubles : favorMap) {
                    latlongs.add(new LatLong(doubles[0], doubles[1]));
                }

                return latlongs;
            } catch (IOException | ClassNotFoundException ex) {
                Log.w("Exception List File", ex.getMessage());
            }
        }
        return null;
    }

    public static void storeLocations(File destination, List<LatLong> list) {
        List<Double[]> favorList = new ArrayList<Double[]>();
        for (LatLong latLong : list) {
            favorList.add(new Double[]{latLong.getLatitude(), latLong.getLongitude()});
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(destination);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(favorList);
            objectOutputStream.close();
        } catch (IOException ex) {
            Log.w("Exception List File", ex.getMessage());
        }
    }
}
