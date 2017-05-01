package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v4.util.LruCache;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.MapFragment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ActivityInterface {

    private final int CAMERA = 0, GALLERY = 1;
    private TextView photoIndex;
    private ImageView image;
    private EditText editText;
    private DatabaseManager dbManager;
    private MapManager mapManager;
    private String newPhotoUri;
    private ArrayList<Integer> idList;
    private LruCache<String, Bitmap> bitmapCache;
    private int arrayIndex;
    private float actionDown;
    private boolean liteMode;

    protected void onSaveInstanceState(Bundle outState) {
        outState.putIntegerArrayList("idList", idList);
        outState.putInt("arrayIndex", arrayIndex);
        outState.putString("editText", editText.getText().toString());
        outState.putString("newPhotoUri", newPhotoUri);
        super.onSaveInstanceState(outState);
    }

    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        idList = inState.getIntegerArrayList("idList");
        arrayIndex = inState.getInt("arrayIndex");
        editText.setText(inState.getString("editText"));
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(mapManager);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        liteMode = preferences.getBoolean("liteMode", false);
        if (liteMode) {
            setContentView(R.layout.activity_lite);
        } else {
            setContentView(R.layout.activity_main);
        }
        // EDIT TEXT AUTOMATICALLY GETS FOCUS. HIDE KEYBOARD.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        mapManager = new MapManager(this, this);
        dbManager = new DatabaseManager(this, this);
        photoIndex = (TextView) findViewById(R.id.photo_index);
        image = (ImageView) findViewById(R.id.image);
        editText = (EditText) findViewById(R.id.search_notes);
        editText.setOnKeyListener(new MapSearch());
        image.setOnTouchListener(new DetectSwipe());

        // CREATE ARRAY LIST OF DATABASE ID'S
        idList = dbManager.getIdList();
        arrayIndex = idList.size() > 0 ? 0 : -1;

        // CREATE MEMORY CACHE TO STORE BITMAPS. MAX SIZE IS 1/8 OF APP MEMORY OR 8MB
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory());
        final int cacheSize = Math.min(maxMemory / 8, (8 * 1024 * 1024));
        bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        // NOTHING ELSE HAPPENS UNTIL GOOGLE MAP IS READY. SEE MAP MANAGER 'ON MAP READY'
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(mapManager);
    }

    // SAVE LAST VIEWED PICTURE SO WE CAN LOAD IT WHEN APP STARTS AGAIN
    // RETRIEVED IN MAP MANAGER 'ON MAP READY'
    public void onPause() {
        super.onPause();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        if (!idList.isEmpty()) {
            editor.putInt("id", idList.get(arrayIndex));
            editor.apply();
        }
    }

    // CLEAR BITMAP CACHE, ALTHOUGH APP CACHE STILL BUILDS UP.
    // TO DO: CLEAR APP CACHE
    public void onDestroy() {
        bitmapCache.evictAll();
        super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        this.getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    // HIDE 'TAKE PHOTO' IF NO CAMERA AVAILABLE
    public boolean onPrepareOptionsMenu(Menu menu) {
        PackageManager packageManager = getPackageManager();
        MenuItem item = menu.findItem(R.id.option_take_photo);
        item.setVisible(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA));
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.option_take_photo:
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) != null) {

                    // IF SUCCESSFULLY CREATED FILE FOR NEW PHOTO THEN CONTINUE
                    File newPhotoFile = getPhotoFile();
                    if (newPhotoFile != null) {
                        Uri uri = FileProvider.getUriForFile(this, "edu.wccnet.jjmoore.photomap", newPhotoFile);
                        newPhotoUri = uri.toString(); // SAVE FOR ON_ACTIVITY_RESULT (AND IN CASE OF ORIENTATION CHANGE)
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

                        // GRANT URI PERMISSIONS TO ALL PACKAGES THAT CAN RESPOND TO ACTION_IMAGE_CAPTURE
                        List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        for (ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }

                        Toast.makeText(this, "Opening Camera", Toast.LENGTH_SHORT).show();
                        startActivityForResult(intent, CAMERA);
                    } else {
                        Toast.makeText(this, "Error Creating Photo File", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No Camera Detected", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.option_add_photo:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType("image/*");
                Toast.makeText(this, "Opening Image Viewer", Toast.LENGTH_SHORT).show();
                startActivityForResult(intent, GALLERY);
                break;
            case R.id.option_save_location:
                mapManager.saveMap(null);
                break;
            case R.id.option_toggle_view:
                startAlertDialog();
                break;
            case R.id.set_preferences:
                DialogPreferences dialog = new DialogPreferences(this, this, liteMode,
                        mapManager.getAnimateCamera(), mapManager.hasBuildings());
                dialog.show();
                break;
            case R.id.option_delete:
                dbManager.delete(idList.get(arrayIndex));
                break;
        }
        return true;
    }

    // CREATE DIRECTORY, PATHNAME AND FILENAME TO SAVE CAMERA PICTURE
    File getPhotoFile() {
        File newPhotoFile = null;
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Photomap");
            path.mkdirs();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "PHOTOMAP_" + timeStamp;
            newPhotoFile = File.createTempFile(imageFileName, ".jpg", path);
        } catch (IOException e) {
            Log.d("Jeremy", "onActivityResult: " + e.toString());
        }
        return newPhotoFile;
    }

    // DIALOG BOX TO SELECT MAP TYPE (HYBRID, SATELLITE, TERRAIN)
    void startAlertDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle(R.string.dialog_mapview);
        DialogInterface.OnClickListener dialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int which) {
                mapManager.setView(which);
            }
        };
        alertBuilder.setItems(R.array.map_type_options, dialog);
        alertBuilder.show();
    }

    /*** BEGIN ON_KEY_LISTENER INNER CLASS ***/
    private class MapSearch implements View.OnKeyListener {

        // SEARCH MAP FROM EDIT TEXT
        // MINOR ISSUE: ALSO USED FOR NOTES AND APP CAN'T DETERMINE IF YOU INTEND TO SEARCH OR NOT
        public boolean onKey(View view, int key_code, KeyEvent event) {
            if (key_code != KeyEvent.KEYCODE_ENTER
                    || event.getAction() != KeyEvent.ACTION_DOWN
                    || editText.getText().length() == 0)
                return false;

            // HIDE KEYBOARD AFTER HITTING ENTER
            InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            mapManager.searchMap(editText.getText().toString());
            return true;
        }
    } // END ON_KEY_LISTENER INNER CLASS

    /*** BEGIN ON_TOUCH_LISTENER INNER CLASS ***/
    private class DetectSwipe implements View.OnTouchListener {

        // DETECT PHOTO SWIPE AND CHANGE RECORD ACCORDINGLY
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // SAVE ACTION_DOWN TO COMPARE WITH ACTION_UP LATER
                    actionDown = event.getX();
                    break;
                case MotionEvent.ACTION_UP:
                    float swipe = actionDown - event.getX();
                    if (swipe > 100 && arrayIndex < (idList.size() - 1)) {
                        arrayIndex++;
                        dbManager.query(null, "id=" + idList.get(arrayIndex));
                    } else if (swipe < -100 && arrayIndex > 0) {
                        arrayIndex--;
                        dbManager.query(null, "id=" + idList.get(arrayIndex));
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }
    } // END ON_TOUCH_LISTENER INNER CLASS

    // COMING BACK FROM EITHER CAMERA OR GALLERY
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
            case CAMERA:
                // SAVE PHOTO FROM CAMERA TO GALLERY
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri cameraUri = Uri.parse(newPhotoUri);
                intent.setData(cameraUri);
                getApplicationContext().revokeUriPermission(cameraUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION); // REMOVE PERMISSIONS WE HAD TO GRANT TO CAMERA
                this.sendBroadcast(intent);
                dbManager.insert(newPhotoUri, "INSERT_FROM_CAMERA");
                break;

            case GALLERY:
                // SAVE PHOTO FROM GALLERY
                // HAD TROUBLE WITH KEEPING PERMISSIONS THROUGH REBOOT. MAKE SURE TO PERSIST PERMISSIONS
                Uri uri = data.getData();
                grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                dbManager.insert(uri.toString(), "INSERT_FROM_GALLERY");
                break;
        }
    }

    // TRY TO GET LOCATION FROM GPS, IF NOT TRY TO GET FROM NETWORK
    // TRIED TO DETECT IF GPS WAS AVAILABLE AND WAS GETTING NULL LOCATION WITH GPS AVAILABLE
    Location getCurrentLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        return location;
    }

    /*** START OF ACTIVITY_INTERFACE METHODS ***/

    // SAVE MAP DATA TO DATABASE
    public void mapToDatabase(double lat, double lng, float zoom, float bearing, float tilt, int type) {
        if (idList.size() > 0) // IN CASE SOMEONE TRIES TO SAVE A LOCATION WHEN NO PHOTOS ARE PRESENT
            dbManager.update(idList.get(arrayIndex), lat, lng, zoom, bearing, tilt, type, editText.getText().toString());
    }

    // LOAD MAP DATA FROM DATABASE
    public void databaseToMap(double lat, double lng, float zoom, float bearing, float tilt, int type, boolean hasMarker) {
        type = (type == 0) ? 1 : type; // TO INSURE MAP TYPE IS NEVER 0 (DISPLAYS BLANK MAP AREA)
        mapManager.updateMap(lat, lng, zoom, bearing, tilt, type, hasMarker);
    }

    // REMOVE ANY RECORDS IF THE PHOTO CANNOT BE ACCESSED.
    // MIGHT BE A BETTER WAY TO HANDLE THIS, BUT IF THERE'S NO PICTURE, THERE'S NO POINT
    public void removeDeletedRecord() {
        dbManager.delete(idList.get(arrayIndex));
        Toast.makeText(this, "Photo not found. Record Deleted", Toast.LENGTH_SHORT).show();
    }

    // STORE PREFERENCES FROM DIALOG BOX. RETRIEVED IN MAP MANAGER 'ON MAP READY'
    // ALSO, IF THEY CHANGE TO LITE MODE, RESTART THE APPLICATION AND CHANGE THE LAYOUT
    public void savePreferences(boolean liteMode, boolean animateCamera, boolean showBuildings) {
        mapManager.setAnimateCamera(animateCamera);
        mapManager.setBuildings(showBuildings);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("liteMode", liteMode);
        editor.putBoolean("animate", animateCamera);
        editor.putBoolean("buildings", showBuildings);
        editor.apply();

        if (this.liteMode != liteMode) {
            this.liteMode = liteMode;
            Intent intent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    // CALLED FROM MAP MANAGER ON_MAP_READY WHEN FIRST START START OR WHEN RESUME
    public void mapToView(int savedDbId) {
        arrayIndex = idList.indexOf(savedDbId);
        if (idList.isEmpty()) {
            mapManager.updateMap();
            image.setImageBitmap(null);
        } else {
            arrayIndex = (arrayIndex == -1) ? 0 : arrayIndex;
            dbManager.query(null, "id=" + idList.get(arrayIndex));
        }

        photoIndex.setText(String.format(getString(R.string.photo_index), arrayIndex + 1, idList.size()));
    }


    // UPDATE VIEW AFTER DATABASE ACTIVITY (INSERT, DELETE, QUERY, ETC...)
    // A LONG METHOD THAT IS VERY IMPORTANT AND MIGHT BE BETTER TO BREAK UP OR MOVE INTO SEPARATE CLASS
    public void databaseToView(String operation, int result, String uri, String description) {

        switch (operation) {
            // CALLED WHEN CHANGING RECORDS (VIA SWIPING)
            case "QUERY":
                updateImage(uri);
                break;

            // CALLED AFTER INSERTING A NEW PHOTO FROM THE GALLERY
            case "INSERT_FROM_GALLERY":
                updateImage(uri);
                idList.add(result);
                arrayIndex = idList.size() - 1;
                mapManager.updateMap();
                break;

            // CALLED AFTER INSERTING A NEW PHOTO FROM THE CAMERA
            case "INSERT_FROM_CAMERA":
                updateImage(uri);
                idList.add(result);
                arrayIndex = idList.indexOf(result);
                Location location = getCurrentLocation();
                if (location == null) {
                    mapManager.updateMap();
                } else {
                    dbManager.update(idList.get(arrayIndex), location.getLatitude(), location.getLongitude(), 16.0F, 0, 0, 1, "");
                    mapManager.updateMap(location.getLatitude(), location.getLongitude(), 16.0F, 0, 0, 1, true);
                }
                break;

            // CALLED AFTER SAVING MAP VIEW OF THE CURRENT PHOTO
            case "UPDATE":
                String toast = result > 0 ? getResources().getString(R.string.location_saved) :
                        getResources().getString(R.string.location_not_saved);
                Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
                break;

            // CALLED AFTER DELETING A PHOTO
            case "DELETE":
                idList.remove(arrayIndex);
                if (arrayIndex == idList.size()) {
                    arrayIndex = idList.size() - 1;
                }
                if (arrayIndex == -1) {
                    mapManager.updateMap();
                    image.setImageBitmap(null);
                } else {
                    dbManager.query(null, "id=" + idList.get(arrayIndex));
                }
                break;
        }
        photoIndex.setText(String.format(getString(R.string.photo_index), arrayIndex + 1, idList.size()));
        editText.setText(description);
    }

    private void updateImage(String uri) {
        // RETRIEVE IMAGE FROM CACHE IF AVAILABLE, OTHERWISE GET FROM CACHE
        Bitmap bitmap = bitmapCache.get(uri);
        if (bitmap == null) {
            new ImageLoader(this, this, image, true).execute(uri);
        } else {
            image.setImageBitmap(bitmap);
        }

        // GET NEXT PHOTO AND PUT IN CACHE
        if ((arrayIndex + 1) < idList.size()) {
            Cursor cursor = dbManager.query(new String[]{"uri"}, "id=" + (arrayIndex + 1));
            if (cursor.moveToFirst()) {
                String nextUriString = cursor.getString(0);
                if (bitmapCache.get(nextUriString) == null) {
                    new ImageLoader(this, this, image, false).execute(nextUriString);
                }
            }
        }
    }

    // ACCESSED FROM IMAGE LOADER
    public void addImageToCache(String uri, Bitmap bitmap) {
        bitmapCache.put(uri, bitmap);
    }

    /* POSSIBLE FUTURE IMPROVEMENTS
     * ADD DATE/TIME
     * SEARCH FOR LOCATION DATA ON EXISTING PHOTOS
     * MAKE PHOTO/MAP RATIO CUSTOMIZABLE
     * SEPARATE MAP NOTES FROM SEARCH TEXT
     */
}


