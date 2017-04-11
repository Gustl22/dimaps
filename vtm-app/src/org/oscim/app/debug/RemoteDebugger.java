package org.oscim.app.debug;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.oscim.app.App;
import org.oscim.app.R;

import java.io.File;
import java.io.IOException;

/**
 * Created by gustl on 11.04.17.
 */

public class RemoteDebugger {
    private static Thread.UncaughtExceptionHandler mUEHandler;

    public static synchronized void sendLoagcatMail(Activity logActivity) {

        // save logcat in file
        File outputFile = new File(App.activity.getExternalCacheDir(),
                "logcat.txt");
        try {
            Process logcat = Runtime.getRuntime().exec(
                    "logcat -d -f " + outputFile.getAbsolutePath());
            logcat.waitFor();
        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (!outputFile.exists() || !outputFile.canRead()) return;

        //send file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // Set type to "email"
        emailIntent.setType("vnd.android.cursor.dir/email");
        String to[] = {"user.rebo@gmail.com"};
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
        // the attachment
        Uri uri = Uri.parse("file://" + outputFile);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        // the mail subject
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Dimaps Crash Info");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Hi Developer, \n\nyour work definitely sucks. Here's my crashlog ;D"
                + "\n\nI know that this isn't anonymous, " +
                "but i insist that you use any sent information exclusively for the purpose of debugging."
                + "\n\nIn addition, i have following improvements:\n\n\nSincerely, \nThe mysterious ticking noise");
        logActivity.startActivity(Intent.createChooser(emailIntent, "Send "
                + logActivity.getResources().getString(R.string.application_name) + " crash report..."));
    }

    public static void setExceptionHandler(final Activity crashActivity) {
        mUEHandler = new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                sendLoagcatMail(crashActivity);
                crashActivity.finish();
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(mUEHandler);
    }
}
