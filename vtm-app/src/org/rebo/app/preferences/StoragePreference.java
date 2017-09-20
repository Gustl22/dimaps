package org.rebo.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import org.rebo.app.App;
import org.rebo.app.R;

import java.io.File;

/**
 * Created by gustl on 23.03.17.
 */

public class StoragePreference extends ListPreference {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StoragePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        final SharedPreferences shareprefs = App.activity.getPreferences(Context.MODE_PRIVATE);
        final Resources resources = App.activity.getResources();

        String storage_preference = shareprefs.getString(
                resources.getString(R.string.preferences_storage_location), "");
        setListPreferenceData(this, storage_preference);


        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                SharedPreferences.Editor editor = shareprefs.edit();
                editor.putString(resources.getString(R.string.preferences_storage_location),
                        (String) newValue);
                editor.apply();
                return true;
            }
        });
    }

    protected static void setListPreferenceData(ListPreference lp, String defaultValue) {
        File[] externalFileDirs = ContextCompat.getExternalFilesDirs(App.activity, null);
        CharSequence[] entries = new CharSequence[externalFileDirs.length];
        CharSequence[] entryValues = new CharSequence[externalFileDirs.length];
        for (int i = 0; i < entries.length; i++) {
            File f = externalFileDirs[i];
            String path = f.getAbsolutePath();
            String systemPath = "/storage/";
            int indexSystem = path.indexOf(systemPath) + systemPath.length();
            int indexAndroid = path.indexOf("/Android/");
            entries[i] = path.substring(indexSystem, indexAndroid);
            entryValues[i] = path;
        }
        lp.setEntries(entries);
        lp.setEntryValues(entryValues);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            lp.setDefaultValue(defaultValue.toCharArray());
        } else {
            lp.setDefaultValue(entries[entries.length - 1]);
        }
    }

    public static File getPreferredStorageLocation() {
        SharedPreferences shareprefs = App.activity.getPreferences(Context.MODE_PRIVATE);
        Resources resources = App.activity.getResources();

        File[] externalFileDirs = ContextCompat.getExternalFilesDirs(App.activity, null);
        String storage_preference = shareprefs.getString(
                resources.getString(R.string.preferences_storage_location),
                externalFileDirs[externalFileDirs.length - 1].getAbsolutePath());
        return new File(storage_preference);
    }
}
