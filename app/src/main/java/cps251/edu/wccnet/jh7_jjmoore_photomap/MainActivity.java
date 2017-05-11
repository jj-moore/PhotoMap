package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.MapFragment;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ActivityInterface {

    // UI FIELDS
    private ImageView photo;
    private TextView photoIndex;
    private TextView photoDate;
    private TextView photoCaption;

    // HELPER CLASSES
    private final DatabaseManager dbManager = new DatabaseManager(this, this);
    private final MapManager mapManager = new MapManager(this, this);

    private final LruCache<String, Bitmap> bitmapCache = initializeBitmapCache();
    private final int CAMERA = 0, GALLERY = 1; // INTENT CONSTANTS
    private ArrayList<Integer> idList;
    private int arrayIndex;
    private String newPhotoUri;
    private float actionDown;
    private boolean liteMode;
    private boolean deleteCache;

    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("arrayIndex", arrayIndex);
        super.onSaveInstanceState(outState);
    }

    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        arrayIndex = inState.getInt("arrayIndex");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        liteMode = preferences.getBoolean("liteMode", false);
        deleteCache = preferences.getBoolean("deleteCache", false);
        if (liteMode) {
            setContentView(R.layout.activity_lite);
        } else {
            setContentView(R.layout.activity_main);
        }

        // INITIALIZE UI FIELDS
        photoIndex = (TextView) findViewById(R.id.photo_index);
        photoDate = (TextView) findViewById(R.id.photo_date);
        photo = (ImageView) findViewById(R.id.image);
        photo.setOnTouchListener(new DetectSwipe());
        photoCaption = (TextView) findViewById(R.id.search_notes);
        photoCaption.setOnClickListener(new EditCaption());

        // CREATE ARRAY LIST OF DATABASE ID'S
        idList = dbManager.getIdList();

        // NOTHING ELSE HAPPENS UNTIL GOOGLE MAP IS READY. SEE MAP MANAGER 'ON MAP READY'
        final MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(mapManager);
    }

    // CREATE MEMORY CACHE TO STORE BITMAPS. MAX SIZE IS 1/8 OF APP MEMORY OR 8MB
    private LruCache<String, Bitmap> initializeBitmapCache() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory());
        final int cacheSize = Math.min(maxMemory / 8, (8 * 1024 * 1024));
        return new LruCache<String, Bitmap>(cacheSize) {
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    // SAVE LAST VIEWED PICTURE SO WE CAN LOAD IT WHEN APP STARTS AGAIN
    // RETRIEVED IN MAP MANAGER 'ON MAP READY'
    public void onPause() {
        super.onPause();
        if (!idList.isEmpty()) {
            final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putInt("id", idList.get(arrayIndex)).apply();
        }
    }

    // CLEAR BITMAP CACHE, CLEAR APP CACHE BUT GOOGLE MAPS STILL MAINTAINS A CACHE
    public void onDestroy() {
        bitmapCache.evictAll();
        deleteDir(getCacheDir());
        if (deleteCache) {
            deleteDir(getExternalCacheDir());
        }
        super.onDestroy();
    }

    // RECURSIVE FUNCTION TO DELETE CACHE
    private void deleteDir(File target) {
        if (target.isDirectory() && target.listFiles().length > 0) {
            for (File file : target.listFiles()) {
                deleteDir(file);
            }
        } else {
            target.delete();
        }
    }

    public void showDatePickerDialog(View view) {
        if (idList.isEmpty()) {
            return;
        }

        // CONVERT DATE_TEXT TO YEAR, MONTH, DAY INTEGERS
        int year = 0, month = 0, day = 0;
        final String dateText = ((TextView) view).getText().toString();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        try {
            final Date date = dateFormat.parse(dateText);
            final Calendar calender = Calendar.getInstance();
            calender.setTime(date);
            year = calender.get(Calendar.YEAR);
            month = calender.get(Calendar.MONTH);
            day = calender.get(Calendar.DAY_OF_MONTH);
        } catch (ParseException e) {
            Log.d("Jeremy", "showDatePickerDialog: " + e.toString());
        }

        // BUNDLE PARAMETERS AND START FRAGMENT
        final Bundle bundle = new Bundle();
        bundle.putInt("year", year);
        bundle.putInt("month", month);
        bundle.putInt("day", day);
        bundle.putInt("id", idList.get(arrayIndex));
        bundle.putSerializable("dbManager", dbManager);
        final DialogFragment newFragment = new DatePicker();
        newFragment.setArguments(bundle);
        newFragment.show(getFragmentManager(), "datePicker");
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        this.getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    // HIDE 'TAKE PHOTO' IF NO CAMERA AVAILABLE
    public boolean onPrepareOptionsMenu(Menu menu) {
        final PackageManager packageManager = getPackageManager();
        final MenuItem item = menu.findItem(R.id.option_take_photo);
        item.setVisible(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA));
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_take_photo:
                final Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (cameraIntent.resolveActivity(getPackageManager()) != null) { //IF DEVICE HAS A CAMERA

                    // IF SUCCESSFULLY CREATED FILE FOR NEW PHOTO THEN CONTINUE
                    final File newPhotoFile = getPhotoFile();
                    if (newPhotoFile != null) {
                        final Uri uri = FileProvider.getUriForFile(this, "edu.wccnet.jjmoore.photomap", newPhotoFile);
                        newPhotoUri = uri.toString(); // SAVE FOR ON_ACTIVITY_RESULT (AND IN CASE OF ORIENTATION CHANGE)
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

                        // GRANT URI PERMISSIONS TO ALL PACKAGES THAT CAN RESPOND TO ACTION_IMAGE_CAPTURE
                        final List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
                        for (ResolveInfo resolveInfo : resInfoList) {
                            grantUriPermission(resolveInfo.activityInfo.packageName, uri,
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        startActivityForResult(cameraIntent, CAMERA);
                    } else {
                        Toast.makeText(this, "Error Creating Photo File", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No Camera Detected", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.option_add_photo:
                final Intent galleryIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                galleryIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                galleryIntent.setType("image/*");
                startActivityForResult(galleryIntent, GALLERY);
                break;
            case R.id.option_save_location:
                if (!idList.isEmpty()) {
                    mapManager.saveMap(null);
                }
                break;
            case R.id.option_toggle_view:
                startAlertDialog();
                break;
            case R.id.set_preferences:
                new DialogPreferences(this, this, liteMode, mapManager.getAnimateCamera(),
                        mapManager.hasBuildings(), deleteCache).show();
                break;
            case R.id.option_delete:
                if (!idList.isEmpty()) {
                    dbManager.delete(idList.get(arrayIndex));
                }
                break;
        }
        return true;
    }

    // CREATE DIRECTORY, PATHNAME AND FILENAME TO SAVE CAMERA PICTURE
    File getPhotoFile() {
        File newPhotoFile = null;
        try {
            final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Photomap");
            path.mkdirs();
            final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String imageFileName = "PHOTOMAP_" + timeStamp;
            newPhotoFile = File.createTempFile(imageFileName, ".jpg", path);
        } catch (IOException e) {
            Log.d("Jeremy", "onActivityResult: " + e.toString());
        }
        return newPhotoFile;
    }

    // DIALOG BOX TO SELECT MAP TYPE (HYBRID, SATELLITE, TERRAIN)
    void startAlertDialog() {
        final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle(R.string.dialog_mapview);
        final DialogInterface.OnClickListener dialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int which) {
                mapManager.setView(which);
            }
        };
        alertBuilder.setItems(R.array.map_type_options, dialog);
        alertBuilder.show();
    }

    /*** BEGIN ON_CLICK_LISTENER INNER CLASS ***/
    private class EditCaption implements View.OnClickListener {

        public void onClick(View view) {
            if (!idList.isEmpty()) {
                new DialogCaption(MainActivity.this, MainActivity.this, photoCaption.getText().toString()).show();
            }
        }

        // SEARCH MAP FROM EDIT TEXT
        public boolean onKey(View view, int key_code, KeyEvent event) {
            if (key_code != KeyEvent.KEYCODE_ENTER
                    || event.getAction() != KeyEvent.ACTION_DOWN
                    || photoCaption.getText().length() == 0)
                return false;

            // HIDE KEYBOARD AFTER HITTING ENTER
            final InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.hideSoftInputFromWindow(photoCaption.getWindowToken(), 0);
            mapManager.searchMap(photoCaption.getText().toString());
            return true;
        }
    } // END ON_KEY_LISTENER INNER CLASS

    /*** BEGIN ON_TOUCH_LISTENER INNER CLASS ***/
    private class DetectSwipe implements View.OnTouchListener {

        // DETECT PHOTO SWIPE AND CHANGE RECORD ACCORDINGLY
        public boolean onTouch(View v, MotionEvent event) {
            if (idList.isEmpty()) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // SAVE ACTION_DOWN TO COMPARE WITH ACTION_UP LATER
                    actionDown = event.getX();
                    break;
                case MotionEvent.ACTION_UP:
                    final float swipe = actionDown - event.getX();
                    if (swipe > 100 && arrayIndex < (idList.size() - 1)) {
                        arrayIndex++;
                        dbManager.updateViewWithId(idList.get(arrayIndex));
                    } else if (swipe < -100 && arrayIndex > 0) {
                        arrayIndex--;
                        dbManager.updateViewWithId(idList.get(arrayIndex));
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
                final Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                final Uri cameraUri = Uri.parse(newPhotoUri);
                intent.setData(cameraUri);
                getApplicationContext().revokeUriPermission(cameraUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION); // REMOVE PERMISSIONS WE HAD TO GRANT TO CAMERA
                this.sendBroadcast(intent);
                dbManager.insert(newPhotoUri, "INSERT_FROM_CAMERA");
                break;

            case GALLERY:
                // SAVE PHOTO FROM GALLERY
                // HAD TROUBLE WITH KEEPING PERMISSIONS THROUGH REBOOT. MAKE SURE TO PERSIST PERMISSIONS
                final Uri uri = data.getData();
                grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                dbManager.insert(uri.toString(), "INSERT_FROM_GALLERY");
                break;
        }
    }

    // TRY TO GET LOCATION FROM GPS, IF NOT TRY TO GET FROM NETWORK
    // TRIED TO DETECT IF GPS WAS AVAILABLE AND WAS GETTING NULL LOCATION WITH GPS AVAILABLE
    Location getCurrentLocation() {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        return location;
    }

    /*** START OF ACTIVITY_INTERFACE METHODS ***/

    // CALLED FROM MAP MANAGER ON_MAP_READY WHEN FIRST START START OR WHEN RESUME
    public void mapToView(int savedDbId) {
        if (idList.isEmpty()) {
            mapManager.updateMap();
            photo.setImageBitmap(null);
            photoIndex.setText(String.format(getString(R.string.photo_index), 0, 0));
        } else {
            arrayIndex = (idList.contains(savedDbId)) ? idList.indexOf(savedDbId) : 0;
            dbManager.updateViewWithId(savedDbId);
            photoIndex.setText(String.format(getString(R.string.photo_index), arrayIndex + 1, idList.size()));
        }
    }

    // SAVE MAP DATA TO DATABASE
    public void mapToDatabase(double lat, double lng, float zoom, float bearing, float tilt, int type) {
        if (!idList.isEmpty()) // IN CASE SOMEONE TRIES TO SAVE A LOCATION WHEN NO PHOTOS ARE PRESENT
            dbManager.updateMap(idList.get(arrayIndex), lat, lng, zoom, bearing, tilt, type);
    }

    // LOAD MAP DATA FROM DATABASE
    public void databaseToMap(double lat, double lng, float zoom, float bearing, float tilt, int type, boolean hasMarker) {
        mapManager.updateMap(lat, lng, zoom, bearing, tilt, type, hasMarker);
    }

    // STORE PREFERENCES FROM DIALOG BOX. RETRIEVED IN MAP MANAGER 'ON MAP READY' AND ON CREATE
    // ALSO, IF THEY CHANGE TO LITE MODE, RESTART THE APPLICATION AND CHANGE THE LAYOUT
    public void savePreferences(boolean liteMode, boolean animateCamera, boolean showBuildings, boolean deleteCache) {
        mapManager.setAnimateCamera(animateCamera);
        mapManager.setBuildings(showBuildings);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("liteMode", liteMode);
        editor.putBoolean("animate", animateCamera);
        editor.putBoolean("buildings", showBuildings);
        editor.putBoolean("deleteCache", deleteCache);
        editor.apply();

        this.deleteCache = deleteCache;
        if (this.liteMode != liteMode) {
            this.liteMode = liteMode;
            final Intent intent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        Toast.makeText(this, "Preferences Saved", Toast.LENGTH_SHORT).show();
    }

    public void saveCaption(CharSequence caption) {
        photoCaption.setText(caption);
        dbManager.updateCaption(idList.get(arrayIndex), caption.toString());
    }

    // UPDATE VIEW AFTER DATABASE ACTIVITY (INSERT, DELETE, QUERY, ETC...)
    public void databaseToView(String operation, int result, String uri, String description, long date) {
        updateImage(uri);
        photoIndex.setText(String.format(getString(R.string.photo_index), arrayIndex + 1, idList.size()));
        final DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);
        photoDate.setText(format.format(new Date(date)));
        photoCaption.setText(description);


        switch (operation) {
            // CALLED WHEN CHANGING RECORDS (VIA SWIPING)
            case "QUERY":
                updateImage(uri);
                break;

            // CALLED AFTER INSERTING A NEW PHOTO FROM THE GALLERY
            case "INSERT_FROM_GALLERY":
                updateImage(uri);
                idList.add(result);
                arrayIndex = idList.indexOf(result);
                mapManager.updateMap();
                break;

            // CALLED AFTER INSERTING A NEW PHOTO FROM THE CAMERA
            case "INSERT_FROM_CAMERA":
                updateImage(uri);
                idList.add(result);
                arrayIndex = idList.indexOf(result);
                final Location location = getCurrentLocation();
                if (location == null) {
                    mapManager.updateMap();
                } else {
                    dbManager.updateMap(result, location.getLatitude(), location.getLongitude(), 16.0F, 0, 0, 1);
                    mapManager.updateMap(location.getLatitude(), location.getLongitude(), 16.0F, 0, 0, 1, true);
                }
                break;

            // CALLED AFTER DELETING A PHOTO
            case "DELETE":
                idList.remove(arrayIndex);
                if (arrayIndex == idList.size()) {
                    arrayIndex--;
                }

                if (arrayIndex == -1) {
                    mapManager.updateMap();
                    photo.setImageBitmap(null);
                } else {
                    dbManager.updateViewWithId(idList.get(arrayIndex));
                }
                break;
        }
    }

    public void addId(int index) {
        idList.add(index);
        arrayIndex = idList.indexOf(index);
    }

    public void removeId(int index) {
        idList.remove(index);
        if (arrayIndex == idList.size()) {
            arrayIndex--;
        }

        if (arrayIndex == -1) {
            mapManager.updateMap();
            photo.setImageBitmap(null);
        } else {
            dbManager.updateViewWithId(idList.get(arrayIndex));
        }
    }

    public void mapSaved(int result) {
        final String toast = result > 0 ? getResources().getString(R.string.location_saved) :
                getResources().getString(R.string.location_not_saved);
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void updateImage(String uri) {
        // RETRIEVE IMAGE FROM CACHE IF AVAILABLE, OTHERWISE GET FROM CACHE
        final Bitmap bitmap = bitmapCache.get(uri);
        if (bitmap == null) {
            new ImageLoader(this, this, photo, true).execute(uri);
        } else {
            photo.setImageBitmap(bitmap);
        }

        // GET NEXT PHOTO AND PUT IN CACHE IF NOT ALREADY THERE
        if ((arrayIndex + 1) < idList.size()) {
            final String uriString = dbManager.getUriString(idList.get(arrayIndex + 1));
            if (bitmapCache.get(uriString) == null) {
                new ImageLoader(this, this, photo, false).execute(uriString);
            }
        }
    }

    // ACCESSED FROM IMAGE LOADER
    public void addImageToCache(String uri, Bitmap bitmap) {
        bitmapCache.put(uri, bitmap);
    }

    /* POSSIBLE FUTURE IMPROVEMENTS
     * SEARCH FOR LOCATION DATA ON EXISTING PHOTOS
     * MAKE PHOTO/MAP RATIO CUSTOMIZABLE
     */
}


