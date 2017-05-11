package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

class DatabaseManager implements Serializable {
    private final Uri DATABASE_URI = Uri.parse("content://cps251.edu.wccnet.jh7_jjmoore_content_provider/photomap");
    private Activity activity;
    private ActivityInterface activityInterface;
    private String uri;
    private String caption;
    private int resultCount;
    private long date;

    DatabaseManager(Activity activity, ActivityInterface activityInterface) {
        this.activity = activity;
        this.activityInterface = activityInterface;
    }

    // GET LIST OF ID'S WHEN APPLICATION STARTS
    ArrayList<Integer> getIdList() {
        ArrayList<Integer> idList = new ArrayList<>();
        CursorLoader loader = new CursorLoader(activity, DATABASE_URI, new String[]{"id"}, null, null, "id");
        Cursor cursor = loader.loadInBackground();
        while (cursor.moveToNext())
            idList.add(cursor.getInt(0));
        return idList;
    }

    void updateViewWithId(int id) {
        final CursorLoader loader = new CursorLoader(activity, DATABASE_URI, null, "id=" + id, null, null);
        final Cursor cursor = loader.loadInBackground();
        if (cursor != null && cursor.moveToFirst()) {
            activityInterface.databaseToView("QUERY", cursor.getCount(),
                    cursor.getString(cursor.getColumnIndex("uri")),
                    cursor.getString(cursor.getColumnIndex("caption")),
                    cursor.getLong(cursor.getColumnIndex("date")));

            if (cursor.isNull(2)) { // MAP NOT SET YET
                activityInterface.databaseToMap(0, 0, 0, 0, 0, 1, false);
            } else {
                activityInterface.databaseToMap(
                        cursor.getDouble(cursor.getColumnIndex("lat")),
                        cursor.getDouble(cursor.getColumnIndex("lng")),
                        cursor.getFloat(cursor.getColumnIndex("zoom")),
                        cursor.getFloat(cursor.getColumnIndex("bearing")),
                        cursor.getFloat(cursor.getColumnIndex("tilt")),
                        cursor.getInt(cursor.getColumnIndex("type")),
                        true);
            }
            cursor.close();
        }
    }

    String getUriString(int id) {
        String uriString = null;
        final CursorLoader loader = new CursorLoader(activity, DATABASE_URI, new String[]{"uri"}, "id=" + id, null, null);
        final Cursor cursor = loader.loadInBackground();
        if (cursor != null && cursor.moveToFirst()) {
            uriString = cursor.getString(0);
            cursor.close();
        }
        return uriString;
    }

    // WHEN INSERTING A NEW RECORD YOU CAN ONLY HAVE A URI.
    // UPDATED MAP LOCATION WHEN USING CAMERA HANDLED SEPARATELY
    void insert(String uri, String operation) {
        this.uri = uri;
        this.getExifDate(Uri.parse(uri));

        final ContentValues values = new ContentValues();
        values.put("operation", operation);
        values.put("uri", uri);
        values.put("date", date);
        new DatabaseWork().execute(values);
    }


    void insertFromCamera(String uri) {
        this.uri = uri;
        this.getExifDate(Uri.parse(uri));


    }

    private void getExifDate(Uri uri) {
        if (DocumentsContract.isDocumentUri(activity.getApplicationContext(), uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String photoId = docId.substring(docId.lastIndexOf(':') + 1);
            final String[] columns = {MediaStore.MediaColumns.DATA};

            final Cursor cursor = MediaStore.Images.Media.query(activity.getContentResolver(),
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, "_id=" + photoId, null);

            if (cursor != null && cursor.moveToFirst()) {
                try {
                    final ExifInterface exif = new ExifInterface(cursor.getString(0));
                    if (exif.getAttribute(ExifInterface.TAG_DATETIME) != null) {
                        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
                        final Date date = dateFormat.parse(exif.getAttribute(ExifInterface.TAG_DATETIME).substring(0, 10));
                        this.date = date.getTime();
                    } else {
                        this.date = Calendar.getInstance().getTimeInMillis();
                    }
                } catch (IOException | ParseException e) {
                    Log.d("Jeremy", "getExifDate: " + e.toString());
                }
                cursor.close();
            }
        }
    }


    void delete(int id) {
        final ContentValues values = new ContentValues();
        values.put("operation", "DELETE");
        values.put("id", id);
        new DatabaseWork().execute(values);
    }

    void updateMap(int id, double lat, double lng, float zoom,
                   float bearing, float tilt, int type) {
        final ContentValues values = new ContentValues();
        values.put("operation", "UPDATE_MAP");
        values.put("id", id);
        values.put("lat", lat);
        values.put("lng", lng);
        values.put("zoom", zoom);
        values.put("bearing", bearing);
        values.put("tilt", tilt);
        values.put("type", type);
        new DatabaseWork().execute(values);
    }

    void updateCaption(int id, String caption) {
        this.caption = caption;
        final ContentValues values = new ContentValues();
        values.put("operation", "UPDATE_CAPTION");
        values.put("id", id);
        values.put("caption", caption);
        new DatabaseWork().execute(values);
    }

    void updateDate(int id, long date) {
        this.date = date;
        final ContentValues values = new ContentValues();
        values.put("operation", "UPDATE_DATE");
        values.put("id", id);
        values.put("date", date);
        new DatabaseWork().execute(values);
    }

    /*** ASYNC INNER CLASS ***/
    private class DatabaseWork extends AsyncTask<ContentValues, Integer, String> {

        protected String doInBackground(ContentValues... values_array) {
            final ContentResolver resolver = activity.getContentResolver();
            final ContentValues values = values_array[0];
            final String operation = values.getAsString("operation");
            values.remove("operation");

            switch (operation) {
                case "INSERT_FROM_GALLERY":
                case "INSERT_FROM_CAMERA":
                    //values.put("uri", values.getAsString("uri"));
                    resultCount = (int) ContentUris.parseId(resolver.insert(DATABASE_URI, values));
                    break;
                case "UPDATE_MAP":
                case "UPDATE_CAPTION":
                case "UPDATE_DATE":
                    resultCount = resolver.update(DATABASE_URI, values, "id=" + values.getAsInteger("id"), null);
                    break;
                case "DELETE":
                    resultCount = resolver.delete(DATABASE_URI, "id=" + values.getAsInteger("id"), null);
                    break;
            }
            return operation;
        }

        protected void onPostExecute(String operation) {
            activityInterface.databaseToView(operation, resultCount, uri, caption, date);
            resultCount = 0;
            uri = null;
            caption = null;
            date = 0;

        }
    }

}

