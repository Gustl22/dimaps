package org.rebo.app.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import org.rebo.app.App;
import org.rebo.app.MapLayers;
import org.rebo.app.R;
import org.rebo.app.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;

public class PoiManager {
    private final SparseArray<File> mPoiFiles = new SparseArray<>();
    private int mPoiFileId;

    public PoiManager() {
        initPoiFiles();
    }

    /**
     * @return current active poi file
     */
    public File getPoiFile() {
        return mPoiFiles.get(mPoiFileId);
    }

    public int getPoiFileId() {
        return mPoiFileId;
    }

    public SparseArray<File> getPoiFiles() {
        return mPoiFiles;
    }

    private void initPoiFiles() {
        ArrayList<File> files = getPoiFilesByAreaFolder(null);
        if (files == null || files.isEmpty())
            setPoiFileId(-1);
        else {
            for (int i = 0; i < files.size(); i++) {
                mPoiFiles.append(i, files.get(i));
            }
            setPoiFileId(0);
        }
    }

    public Integer getPoiFileIdByPath(String path) {
        for (int i = 0; i < mPoiFiles.size(); i++) {
            if (path.equals(mPoiFiles.valueAt(i).getAbsolutePath())) {
                return mPoiFiles.keyAt(i);
            }
        }
        return null;
    }

    public ArrayList<File> getPoiFilesByAreaFolder(File areaFolder) {
        ArrayList<File> files;
        if (areaFolder == null || !areaFolder.exists()) {
            files = new ArrayList<>();
            if (MapLayers.MAP_FOLDERS == null) return null;
            for (File f : MapLayers.MAP_FOLDERS) {
                files.addAll(FileUtils.walkExtension(f, ".poi"));
            }
        } else {
            files = FileUtils.walkExtension(areaFolder, ".poi");
        }
        return files;
    }

    /**
     * Sets POI-File id for current search
     *
     * @param fileId the file id, which should be set
     */
    public void setPoiFileId(int fileId) {
        if (mPoiFiles.get(fileId) == null) return;
        mPoiFileId = fileId;
    }

    public void loadPreferences(Context context) {
        SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
        String filepath = sharedPref.getString(context.getString(R.string.pref_poiArea_folderPath), null);
        if (filepath != null) {
            Integer poiFileId = getPoiFileIdByPath(filepath);
            if (poiFileId != null)
                setPoiFileId(poiFileId);
        }
    }

    public void savePreferences(Context context) {
        File poiFile = getPoiFile();
        if (poiFile != null) {
            SharedPreferences sharedPref = App.activity.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(context.getString(R.string.pref_poiArea_folderPath), poiFile.getAbsolutePath());
            editor.apply();
        }
    }
}
