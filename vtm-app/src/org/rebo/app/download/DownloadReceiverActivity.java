package org.rebo.app.download;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.graphhopper.util.Unzipper;

import org.rebo.app.App;
import org.rebo.app.graphhopper.GHAsyncTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gustl on 11.03.17.
 * Activity can be used to receive Download events, which were triggered before
 */

public abstract class DownloadReceiverActivity extends AppCompatActivity {
    private BroadcastReceiver receiver;
    private static List<Long> mDownLoadList;

    protected List<Long> getDownLoadIntList(){
        return mDownLoadList;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState,
                         @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        if(mDownLoadList == null){
            mDownLoadList = new ArrayList<Long>();
        }
        initDownloadReceiver();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(mDownLoadList == null){
            mDownLoadList = new ArrayList<Long>();
        }
        initDownloadReceiver();
    }

    private void initDownloadReceiver() {
        receiver = new BroadcastReceiver() {
            public void onReceive(final Context ctxt, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
                if (!mDownLoadList.contains(id)) {
                    return;
                }
                DownloadManager downloadManager = (DownloadManager) ctxt.getSystemService(
                        Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                Cursor cursor = downloadManager.query(query);

                if (!cursor.moveToFirst()) {
                    Log.w("Download", "Cursor set failed");
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    Log.w("Download", "Download Failed");
                    return;
                }

                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                File mFile = new File(Uri.parse(cursor.getString(uriIndex)).getPath());
                if (mFile.getName().endsWith(".ghz")) {
                    unzipAsync(mFile, ctxt);
                } else {
                    showDownloadCompleted();
                }

            }
        };
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public void unzipAsync(File mFile, final Context ctxt) {
        new GHAsyncTask<File, Void, Boolean>() {
            ProgressDialog progDailog;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progDailog = new ProgressDialog(ctxt);
                progDailog.setMessage("Unzipping...");
                progDailog.setIndeterminate(false);
                progDailog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progDailog.setCancelable(false);
                progDailog.show();
            }

            protected Boolean saveDoInBackground(File... files)
                    throws Exception {
                Unzipper unzipper = new Unzipper();
                if(files[0] == null || files[0].getAbsolutePath().isEmpty())
                    return false;
                return unzipper.unzip(files[0].getAbsolutePath(), files[0].getAbsolutePath()
                        .substring(0, files[0].getAbsolutePath().length() - 4)+"-gh", true);
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progDailog.dismiss();
                if (success == null || !success) {
                    Log.e("Unzipping failed", "File does not exists");
                    return;
                }
                showDownloadCompleted();
            }
        }.execute(mFile);
    }

    private Snackbar snackbar;
    private void showDownloadCompleted() {
        snackbar = Snackbar.make(findViewById(android.R.id.content),
                "Download and unzipping completed", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("Restart", new SnackBarRestartListenter()).show();
    }

    private class SnackBarRestartListenter implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //snackbar.dismiss();
            App.activity.recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }
}
