package org.oscim.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public class ConnectionHandler extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOnline()) {
            Toast.makeText(context, "Active Network Type : " , //+ activeNetInfo.getTypeName()
                    Toast.LENGTH_SHORT).show();
            //if (App.map != null)
            //    App.map.redrawMap();
        }
    }

    public static boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) App.activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }
}
