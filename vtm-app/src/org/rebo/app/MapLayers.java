/*
 * Copyright 2016 devemux86
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.rebo.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.oscim.android.cache.TileCache;
import org.oscim.layers.GenericLayer;
import org.oscim.layers.Layer;
import org.oscim.layers.TileGridLayer;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.buildings.S3DBLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.theme.ThemeFile;
import org.oscim.theme.VtmThemes;
import org.oscim.tiling.ITileCache;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.UrlTileSource;
import org.oscim.tiling.source.bitmap.DefaultSources;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MultiMapFileTileSource;
import org.oscim.tiling.source.oscimap4.OSciMap4TileSource;
import org.rebo.app.download.MapDownloadActivity;
import org.rebo.app.preferences.StoragePreference;
import org.rebo.app.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapLayers {
    static String getDbMapName() {
        return "MAPSFORGE_OFFLINE";
    }

    public static File[] MAP_FOLDERS;
    final static boolean USE_CACHE = true;
    final static boolean USE_S3DB = true;

    //final static Logger log = LoggerFactory.getLogger(MapLayers.class);
    static Config[] configs = new Config[]{new Config("OPENSCIENCEMAP4") {
        TileSource init() {
            return new OSciMap4TileSource();
        }
    }, new Config("MAPSFORGE") {
        TileSource init() {
            return new MapFileTileSource().setOption("file",
                    StoragePreference.getPreferredStorageLocation().getAbsolutePath() + "/maps/openscience.map");
        }
    }, new Config("S3DB") {
        TileSource init() {
            return OSciMap4TileSource.builder()
                    .url("http://opensciencemap.org/tiles/s3db")
                    .zoomMin(17)
                    .zoomMax(17)
                    .build();
        }
    }, new Config("MAPSFORGE_OFFLINE") {
        TileSource init() {
            MultiMapFileTileSource MultiTS = new MultiMapFileTileSource();
            ArrayList<File> files = new ArrayList<File>();
            for (File f : MAP_FOLDERS) {
                files.addAll(FileUtils.walkExtension(f, ".map"));
            }
            if (files.isEmpty()) {
                App.activity.showToastOnUiThread("No maps downloaded.");
                //TODO correct method to start intent
                App.activity.startActivity(new Intent(App.activity, MapDownloadActivity.class));
            } else {
                File worldmap = null;
                for (File f : files) {
                    if (f.getName().toLowerCase().contains("world.map"))
                        worldmap = f;
                }
                if (worldmap == null) {
                    App.activity.showToastOnUiThread("Make shure you downloaded a world map, too!");
                }
            }
            for (File f : files) {
                Log.d("Files", "FileName:" + f.getName());
                    MapFileTileSource ts = new MapFileTileSource();
                    ts.setMapFile(f.getAbsolutePath());
                    MultiTS.add(ts);
            }
            if (MultiTS.getTileSize() > 0) return MultiTS;
            return null;
        }
    }
    };

    private ArrayList<String> mapAreas = new ArrayList<String>();
    private VectorTileLayer mBaseLayer;
    private String mMapDatabase;
    private ITileCache mCache;
    private TileCache mS3dbCache;
    private GenericLayer mGridOverlay;
    private boolean mGridEnabled;
    // FIXME -> implement LayerGroup
    private int mBackgroundId = -2;
    private Layer mBackroundPlaceholder;
    private Layer mBackgroundLayer;

    public MapLayers() {
        mBackroundPlaceholder = new Layer(null) {
        };
        setBackgroundMap(-1);

        MAP_FOLDERS = ContextCompat.getExternalFilesDirs(App.activity, null);
        for (int i = 0; i < MAP_FOLDERS.length; i++) {
            File tmpFile = new File(MAP_FOLDERS[i], "maps/");
            if (tmpFile.exists() || tmpFile.mkdirs()) {
                MAP_FOLDERS[i] = tmpFile;
            } else {
                MAP_FOLDERS[i] = null;
            }
        }
        MAP_FOLDERS = removeNullFile(MAP_FOLDERS);

        //Unzip downloaded Files:
        ArrayList<File> files = new ArrayList<File>();
        for (File f : MAP_FOLDERS) {
            files.addAll(FileUtils.walkExtension(f, ".ghz"));
        }
        for (File f : files) {
            App.activity.unzipAsync(f, App.activity);
        }
    }

    public static File[] removeNullFile(File[] array) {
        List<File> list = new ArrayList<>();

        for (File s : array) {
            if (s != null) {
                list.add(s);
            }
        }
        return list.toArray(new File[list.size()]);
    }

    void setBaseMap(Context context) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        //Temporary files
        String dbname = preferences.getString("mapDatabase", getDbMapName());

        if (dbname.equals(mMapDatabase) && mBaseLayer != null)
            return;

        TileSource tileSource = null;
        for (Config c : configs)
            if (c.name.equals(getDbMapName()))
                tileSource = c.init();

        if (tileSource == null) {
            tileSource = configs[0].init();
            dbname = getDbMapName();
            preferences.edit().putString("mapDatabase", dbname).commit();
        }

        //Cache could be set with an boolean, S3DB would not work with cache
        if(USE_CACHE){
            if (tileSource instanceof UrlTileSource) {
                mCache = new TileCache(App.activity, context.getExternalCacheDir().getAbsolutePath(), dbname);
                mCache.setCacheSize(512 * (1 << 10));
                tileSource.setCache(mCache);
            } else {
                mCache = null;
            }
        }

        if (mBaseLayer == null) {
            mBaseLayer = App.map.setBaseMap(tileSource); //Base Layer (almost OPENSCIENCEMAP4)
            if(ConnectionHandler.isOnline() && USE_S3DB){
                TileSource s3dbTileSource = configs[2].init();
                if (USE_CACHE) {
                    mS3dbCache = new TileCache(App.activity, context.getExternalCacheDir().getAbsolutePath(), "s3db.db");
                    mS3dbCache.setCacheSize(512 * (1 << 10));
                    s3dbTileSource.setCache(mS3dbCache);
                }
                App.map.layers().add(2, new S3DBLayer(App.map, s3dbTileSource, true, false));
            } else {
                App.map.layers().add(2, new BuildingLayer(App.map, mBaseLayer));
            }
            App.map.layers().add(3, new LabelLayer(App.map, mBaseLayer));
        } else
            mBaseLayer.setTileSource(tileSource);

        mMapDatabase = dbname;
    }

    void setPreferences(Context context) {
        setBaseMap(context);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        ThemeFile theme = VtmThemes.DEFAULT;
        if (preferences.contains("theme")) {
            String name = preferences.getString("theme", "DEFAULT");
            try {
                theme = VtmThemes.valueOf(name);
            } catch (IllegalArgumentException e) {
                theme = VtmThemes.DEFAULT;
            }
        }

        App.map.setTheme(theme);

        // default cache size 20MB
        int cacheSize = preferences.getInt("cacheSize", 20);

        if (mCache != null)
            mCache.setCacheSize(cacheSize * (1 << 20));

    }

    void enableGridOverlay(Context context, boolean enable) {
        if (mGridEnabled == enable)
            return;

        if (enable) {
            if (mGridOverlay == null)
                mGridOverlay = new TileGridLayer(App.map, context.getResources().getDisplayMetrics().density);

            App.map.layers().add(mGridOverlay);
        } else {
            App.map.layers().remove(mGridOverlay);
        }

        mGridEnabled = enable;
        App.map.updateMap(true);
    }

    boolean isGridEnabled() {
        return mGridEnabled;
    }

    void setBackgroundMap(int id) {
        if (id == mBackgroundId)
            return;

        App.map.layers().remove(mBackgroundLayer);
        mBackgroundLayer = null;

        switch (id) {
            case R.id.menu_layer_openstreetmap:
                mBackgroundLayer = new BitmapTileLayer(App.map, DefaultSources.OPENSTREETMAP.build());
                break;

            case R.id.menu_layer_naturalearth:
                mBackgroundLayer = new BitmapTileLayer(App.map, DefaultSources.NE_LANDCOVER.build());
                break;
            default:
                mBackgroundLayer = mBackroundPlaceholder;
                id = -1;
        }

        if (mBackgroundLayer instanceof BitmapTileLayer)
            App.map.setBaseMap((BitmapTileLayer) mBackgroundLayer);
        else
            App.map.layers().add(1, mBackroundPlaceholder);

        mBackgroundId = id;
    }

    int getBackgroundId() {
        return mBackgroundId;
    }

    public void deleteCache() {
        if (mCache != null)
            mCache.setCacheSize(0);
    }

    abstract static class Config {
        final String name;

        public Config(String name) {
            this.name = name;
        }

        abstract TileSource init();
    }
}
