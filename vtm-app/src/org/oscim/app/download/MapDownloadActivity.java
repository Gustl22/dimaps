package org.oscim.app.download;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.unnamed.b.atv.model.TreeNode;
import com.unnamed.b.atv.view.AndroidTreeView;

import org.oscim.app.App;
import org.oscim.app.R;
import org.oscim.app.graphhopper.GHAsyncTask;
import org.oscim.app.holder.SelectableHeaderHolder;
import org.oscim.app.holder.SelectableItemHolder;
import org.oscim.app.holder.AreaFileInfo;
import org.oscim.app.preferences.StoragePreference;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.oscim.app.ConnectionHandler.isOnline;


/**
 * Created by Gustl on 06.03.2017.
 */

public class MapDownloadActivity extends DownloadReceiverActivity implements AdapterView.OnItemSelectedListener {

    //Inidcates the downloaded folder structure in your application files
    private final boolean isGraphhopperFolderStyle = true;
    //Download Folder
    private File mMapsFolder;

    private Button remoteButton;
    private LinearLayout listViewWrapper;
    private RelativeLayout map_list;
    //    private String fileListURL = "http://download2.graphhopper.com/public/maps/" + Constants.getMajorVersion() + "/";
    private String fileListURLroot;
    private String downloadURL;
    private HashMap<String, String> dSources;
    BroadcastReceiver onDownloadComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_maps);

        listViewWrapper = (LinearLayout) findViewById(R.id.treeView_downloadMaps);
        map_list = (RelativeLayout) findViewById(R.id.map_list);
        remoteButton = (Button) findViewById(R.id.remote_button);

        initDownloadSourceSelection();
        initStorageHandling();
        initToolbar();
        if(!isOnline()){
            Toast.makeText(this, "You are offline. You can't download anything!", Toast.LENGTH_LONG).show();
        }
        // TODO get user confirmation to download
        // if (AndroidHelper.isFastDownload(this))
        //chooseAreaFromRemote();
    }



    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String version = "Version unkown";
        try {
            PackageInfo pInfo = App.activity.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            Log.d("Version Error", ex.getMessage());
        }
        toolbar.setTitle("Downloadcenter");
        toolbar.setSubtitle("OpenDimensionMaps " + version + "!");
    }

    private void initStorageHandling() {
        //persistent folder handling
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.d("", R.string.application_name + " is not usable without an external storage!");
            return;
        }
        mMapsFolder = new File(StoragePreference.getPreferredStorageLocation().getAbsolutePath(),
                "/maps/");
        if (!mMapsFolder.exists())
            mMapsFolder.mkdirs();
    }

    private void initDownloadSourceSelection() {
        //DownloadSource Selection
        String[] dSourceesStringArr = getResources().getStringArray(R.array.downloadSources);
        dSources = new HashMap<String, String>();
        String initalMapSource = null;
        for (String s : dSourceesStringArr) {
            String[] tmp = s.split("\\|");
            dSources.put(tmp[0], tmp[1]);
            if (initalMapSource == null) {
                initalMapSource = tmp[0];
            }
        }

        Spinner downloadSpinner = (Spinner) findViewById(R.id.dSourceSpinner);
        downloadSpinner.setOnItemSelectedListener(this);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item);
        adapter.addAll(dSources.keySet());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        downloadSpinner.setAdapter(adapter);
        downloadSpinner.setSelection(adapter.getPosition(initalMapSource));
    }

    /**
     *
     * @param rootPath the path were the first element is located
     * @param filepath the subpaths which were not stored in rootPath.
     *                 At the beginning it's an empty String
     * @return All Files/Folders in the rootspecified folder
     */
    private Collection<String> RecursiveAreaFinder(String rootPath, String filepath) {
        Collection<String> res = new HashSet<String>();
        try {
            String[] lines = new AndroidDownloader().downloadAsString(rootPath+filepath, false).split("\n");
            for (String str : lines) {
                int index = str.indexOf("href=\"");
                if (index >= 0) {
                    index += 6;
                    String subs = str.substring(index);
                    String file;
                    if ((file = getCheckFileType(subs, ".map")) != null) {
                        res.add(rootPath+filepath + file);
                        fileSizeHMap.put((filepath+file).toLowerCase(), getFileSizeFromHtml(subs));
                    } else if ((file = getCheckFileType(subs, ".poi")) != null) {
                        res.add(rootPath+filepath + file);
                        fileSizeHMap.put((filepath+file).toLowerCase(), getFileSizeFromHtml(subs));
                    } else if ((file = getCheckFileType(subs, ".ghz")) != null) {
                        res.add(rootPath+filepath + file);
                        fileSizeHMap.put((filepath+file).toLowerCase(), getFileSizeFromHtml(subs));
                    } else if ((file = getCheckFileType(subs, ".zip")) != null) {
                        res.add(rootPath+filepath + file);
                        fileSizeHMap.put((filepath+file).toLowerCase(), getFileSizeFromHtml(subs));
                    } else {
                        int lastIndex = subs.indexOf("/\">");
                        if (lastIndex >= 0 && str.contains("alt=\"[DIR]\"")) {
                            String subdir = subs.substring(0, lastIndex);
                            if (!subdir.trim().isEmpty()) {
                                String dir = filepath + subdir + "/";
                                res.addAll(RecursiveAreaFinder(rootPath, dir));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
        return res;
    }

    /**
     * Gets the filesize based on HTML "<td" string
     * @param subs Substring of HTML Line
     * @return Filesize included in String
     */
    private String getFileSizeFromHtml(String subs){
        if(subs.contains("<td")){
            String[] arr = subs.split("\\<td");
            for(int i=0; i<arr.length; i++){
                arr[i] = arr[i].substring(arr[i].indexOf(">") + 1, arr[i].indexOf("</td>"));
            }
            if(arr.length>1){
                return arr[2];
            }
        }
        return "-";
    }

    private String getCheckFileType(String str, String filetype) {
        int lastIndex = str.indexOf(filetype);
        if (lastIndex >= 0) {
            return str.substring(0, lastIndex) + filetype;
        }
        return null;
    }

    ProgressDialog progDailog;
    private HashMap fileSizeHMap;
    private void chooseAreaFromRemote() {
        final Context context = this;
        new GHAsyncTask<Void, Void, Collection<String>>() {


            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progDailog = new ProgressDialog(context);
                progDailog.setMessage("Loading...");
                progDailog.setIndeterminate(false);
                progDailog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progDailog.setCancelable(false);
                progDailog.show();
            }

            protected Collection<String> saveDoInBackground(Void... params)
                    throws Exception {
                fileSizeHMap = new HashMap();
                return RecursiveAreaFinder(fileListURLroot, "");
            }

            @Override
            protected void onPostExecute(Collection<String> nameList) {
                progDailog.dismiss();
                super.onPostExecute(nameList);
                if (hasError()) {
                    getError().printStackTrace();
                    Log.d("", "Are you connected to the internet? Problem while fetching remote area list: "
                            + getErrorMessage());
                    return;
                } else if (nameList == null || nameList.isEmpty()) {
                    Log.d("", "No maps created for your version!? " + fileListURLroot);
                    return;
                }

                DownloadListener downloadListener = new DownloadListener() {
                    @Override
                    public void onSelect(AreaFileInfo tnc, String selectedFile) {
                        if (selectedFile == null
                                || new File(mMapsFolder, tnc.getPath()).exists()
/*                                || new File(mMapsFolder, selectedArea + "/").exists()*/) {
                            downloadURL = null;
                        } else {
                            downloadURL = selectedFile;
                        }
                        downloadingFiles(tnc);
                    }
                };
                chooseArea(remoteButton, nameList,
                        downloadListener);
            }
        }.execute();
    }

    private void chooseArea(Button downloadButton,
                            Collection<String> nameList, final DownloadListener myListener) {
        final Map<String, String> nameToPath = new TreeMap<>();
        for (String fullName : nameList) {
            String tmp = fullName;
//            String tmp = Helper.pruneFileEnd(fullName); //remove .map
//            if (tmp.endsWith("/"))
//                tmp = tmp.substring(0, tmp.length() - 1);

            //tmp = AndroidHelper.getFileName(tmp);
            tmp = tmp.substring(fileListURLroot.length()); //keep structure but remove adress
            nameToPath.put(tmp.toLowerCase(), fullName);
        }
        nameList.clear();
        nameList.addAll(nameToPath.keySet());

        //Expandable List
        TreeNode root = TreeNode.root();

        for (String map : nameList) {
            String[] arr = map.split("/");
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].isEmpty()) break;
                arr[i] = arr[i].substring(0, 1).toUpperCase() + arr[i].substring(1); //Set first letter to upper case
                TreeNode parent = root;
                for (int j = 0; j < i; j++) {
                    for (TreeNode t : parent.getChildren()) {
                        if (arr[j].equals(((AreaFileInfo) t.getValue()).getFullName())) {
                            parent = t;
                            break;
                        }
                    }
                }
                if (!TreeNodeListContainsTitle(parent.getChildren(), arr[i])) {
                    //Set icon if is map
                    AreaFileInfo tnc;
                    String tmp = arr[i];
                    for (int j = i - 1; j >= 0; j--) {
                        tmp = arr[j] + '/' + tmp;
                    }
                    if (tmp.endsWith(".map")) {
                        tnc = new AreaFileInfo(R.string.ic_map, tmp);
                    } else if (tmp.endsWith(".ghz")) {
                        tnc = new AreaFileInfo(R.string.ic_archive, tmp);
                    } else if (tmp.endsWith(".poi")) {
                        tnc = new AreaFileInfo(R.string.ic_local_activity, tmp);
                    } else {
                        tnc = new AreaFileInfo(tmp);
                    }
                    tnc.setSize((String) fileSizeHMap.get(tnc.getPath().toLowerCase()));
                    parent.addChild(new TreeNode(tnc));
                }
            }
        }


        SetTreeNodeHolderRecursive(root, 0); //set before view is edit
        final AndroidTreeView tView = new AndroidTreeView(this, root);
        //tView.setDefaultAnimation(true);
        //tView.setUse2dScroll(true);
        tView.setDefaultContainerStyle(R.style.TreeNodeStyleCustom);
        tView.setSelectionModeEnabled(true);

        initSelectableTreeView(listViewWrapper, tView, this, root);
        map_list.removeAllViews();
        map_list.addView(tView.getView());

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Loop each selected item and revert it from nameToPath map to download-path. Then trigger myListener to Download files
                for (TreeNode t : tView.getSelected()) {
                    AreaFileInfo o = ((AreaFileInfo) t.getValue());
                    String area = o.getPath();
                    if (area != null && area.length() > 0 && !nameToPath.isEmpty()) {
                        myListener.onSelect(o, nameToPath.get(area.toLowerCase()));
                    } else {
                        myListener.onSelect(null, null);
                    }
                }
            }
        });
    }


    private void initSelectableTreeView(View rootView, final AndroidTreeView tView, final Context context, final TreeNode root) {

//        View selectionModeButton = rootView.findViewById(R.id.btn_toggleSelection);
//        selectionModeButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                tView.setSelectionModeEnabled(!tView.isSelectionModeEnabled());
//            }
//        });

        View selectAllBtn = rootView.findViewById(R.id.btn_selectAll);
        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tView.isSelectionModeEnabled()) {
                    Toast.makeText(context, "Enable selection mode first", Toast.LENGTH_SHORT).show();
                }
                int counter = 0;
                for (TreeNode t : root.getChildren()) {
                    if (t.isSelected()) counter += 1;
                }
                if (counter == root.getChildren().size()) {
                    tView.deselectAll();
                } else {
                    tView.selectAll(true);
                }
            }
        });
//
//        View deselectAll = rootView.findViewById(R.id.btn_deselectAll);
//        deselectAll.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!tView.isSelectionModeEnabled()) {
//                    Toast.makeText(context, "Enable selection mode first", Toast.LENGTH_SHORT).show();
//                }
//                tView.deselectAll();
//            }
//        });

        View check = rootView.findViewById(R.id.btn_checkSelection);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tView.isSelectionModeEnabled()) {
                    Toast.makeText(context, "Enable selection mode first", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, tView.getSelected().size() + " selected", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public boolean TreeNodeListContainsTitle(final List<TreeNode> list, final String title) {
        boolean isPresent = false;
        for (TreeNode t : list) {
            if (title.equals(((AreaFileInfo) t.getValue()).getFullName())) {
                isPresent = true;
                break;
            }
        }
        return isPresent;
    }

    public void SetTreeNodeHolderRecursive(TreeNode n, int step) {
        List<TreeNode> list = n.getChildren();
        if (!list.isEmpty()) {
            for (TreeNode t : list) {
                if (!t.isLeaf()) {
                    SetTreeNodeHolderRecursive(t, step + 1);
                    t.setViewHolder(new SelectableHeaderHolder(this));
                } else {
                    t.setViewHolder(new SelectableItemHolder(this));
                }
            }
        }
    }

    void downloadingFiles(AreaFileInfo tnc) {
        String downloadPath;
        if (isGraphhopperFolderStyle) {
            String r = tnc.getExtension();
            if (tnc.getExtension().equals(".map") || tnc.getExtension().equals(".poi")) {
                String ghpath = tnc.getPath().substring(0, tnc.getPath().length() - tnc.getExtension().length()).replace("/", "_");
                ghpath = ghpath + "-gh/" + ghpath + tnc.getExtension();
                downloadPath = ghpath;
            } else {
                downloadPath = tnc.getPath();
            }
        } else {
            downloadPath = tnc.getPath();
        }
        File areaFile = new File(mMapsFolder, downloadPath.toLowerCase());
        if (downloadURL == null || areaFile.exists()) {
            return;
        }
        //Gingerbread downloader
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadURL));
        request.setDescription("Map Download");
        request.setTitle("Download " + (tnc.getRegion()==null ? tnc.getText() : tnc.getRegion()) + "-Map");
// in order for this if to run, you must use the android 3.2 to compile your app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        }
        request.setDestinationUri(Uri.fromFile(areaFile));

// get download service and enqueue file
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        getDownLoadIntList().add(manager.enqueue(request));
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        fileListURLroot = dSources.get((String) parent.getItemAtPosition(position));
        chooseAreaFromRemote();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public interface DownloadListener {
        void onSelect(AreaFileInfo tnc, String selectedFile);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progDailog.dismiss();
    }
}
