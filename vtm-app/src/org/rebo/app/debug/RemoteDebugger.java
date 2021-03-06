package org.rebo.app.debug;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.rebo.app.App;
import org.rebo.app.R;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by gustl on 11.04.17.
 */

public class RemoteDebugger {
    private static Thread.UncaughtExceptionHandler mUEHandler;
    private static boolean isFirstLog = true;

    public static synchronized void sendLogcatMail(Activity logActivity, Thread t, Throwable te) {
        if (!isFirstLog) return;
        isFirstLog = false;
        // save logcat in file
        File outputFile = new File(App.activity.getExternalCacheDir(),
                "logcat.txt");
        if (outputFile.exists())
            outputFile.delete();
//        try {
//            Process logcat = Runtime.getRuntime().exec(
//                    "logcat *:W -d -f " + outputFile.getAbsolutePath());
//            logcat.waitFor();
//            Runtime.getRuntime().exec("logcat -c");
//        } catch (IOException | InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        try {
            PrintWriter writer = new PrintWriter(outputFile, "UTF-8");
            writer.println("Process: " + t.getName() + "\n");

            writer.printf(te.getCause().getMessage());
            StackTraceElement[] stackTraceCause = te.getCause().getStackTrace();
            for (StackTraceElement stackTraceElement : stackTraceCause) {
                writer.println("\tat " + stackTraceElement.toString());
            }
            writer.println();

            StackTraceElement[] stackTrace = te.getStackTrace();
            writer.printf(te.getMessage());
            for (StackTraceElement stackTraceElement : stackTrace) {
                writer.println("\tat " + stackTraceElement.toString());
            }
            writer.close();
        } catch (IOException e) {
            return;
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
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Dimaps Crash Report");
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
                sendLogcatMail(crashActivity, t, e);
                crashActivity.finish();
            }
        };
        Thread.currentThread().setUncaughtExceptionHandler(mUEHandler);
    }
}
