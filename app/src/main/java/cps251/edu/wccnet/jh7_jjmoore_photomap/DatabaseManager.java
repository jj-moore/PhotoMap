package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import java.util.ArrayList;

class DatabaseManager {
    private final Uri DATABASE_URI = Uri.parse("content://cps251.edu.wccnet.jh7_jjmoore_content_provider/photomap");
    private Activity activity;
    private ActivityInterface activityInterface;
    private String uri;
    private String description;
    private int resultCount;

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

    Cursor query(String[] columns, String where) {
        CursorLoader loader = new CursorLoader(activity, DATABASE_URI, columns, where, null, "id");
        Cursor cursor = loader.loadInBackground();
        if (cursor.moveToFirst()) {
            if (cursor.isNull(1)) {
                activityInterface.removeDeletedRecord();
            } else {
                activityInterface.databaseToView("QUERY", 1, cursor.getString(1), cursor.getString(8));
                if (cursor.isNull(2)) {
                    activityInterface.databaseToMap(0, 0, 0, 0, 0, 1, false);
                } else {
                    activityInterface.databaseToMap(cursor.getDouble(2), cursor.getDouble(3), cursor.getFloat(4),
                            cursor.getFloat(5), cursor.getFloat(6), cursor.getInt(7), true);
                }
            }
        }
        return cursor;
    }

    // WHEN INSERTING A NEW RECORD YOU CAN ONLY HAVE A URI.
    // UPDATED MAP LOCATION WHEN USING CAMERA HANDLED SEPARATELY
    void insert(String uri, String operation) {
        this.uri = uri;
        ContentValues values = new ContentValues();
        values.put("operation", operation);
        values.put("uri", uri);
        new DatabaseWork().execute(values);
    }

    void delete(int id) {
        ContentValues values = new ContentValues();
        values.put("operation", "DELETE");
        values.put("id", id);
        new DatabaseWork().execute(values);
    }

    void update(int id, double lat, double lng, float zoom, float bearing,
                float tilt, int type, String description) {
        this.description = description;
        ContentValues values = new ContentValues();
        values.put("operation", "UPDATE");
        values.put("id", id);
        values.put("lat", lat);
        values.put("lng", lng);
        values.put("zoom", zoom);
        values.put("bearing", bearing);
        values.put("tilt", tilt);
        values.put("type", type);
        values.put("description", description);
        new DatabaseWork().execute(values);
    }

    /*** ASYNC INNER CLASS ***/
    private class DatabaseWork extends AsyncTask<ContentValues, Integer, String> {

        protected String doInBackground(ContentValues... values_array) {
            int id;
            ContentValues values = values_array[0];
            String operation = values.getAsString("operation");
            values.remove("operation");
            ContentResolver resolver = activity.getContentResolver();

            switch (operation) {
                case "INSERT_FROM_GALLERY":
                case "INSERT_FROM_CAMERA":
                    values.put("uri", values.getAsString("uri"));
                    resultCount = (int) ContentUris.parseId(resolver.insert(DATABASE_URI, values));
                    break;
                case "UPDATE":
                    id = values.getAsInteger("id");
                    resultCount = resolver.update(DATABASE_URI, values, "id=" + id, null);
                    break;
                case "DELETE":
                    id = values.getAsInteger("id");
                    resultCount = resolver.delete(DATABASE_URI, "id=" + id, null);
                    break;
            }
            return operation;
        }

        protected void onPostExecute(String operation) {
            activityInterface.databaseToView(operation, resultCount, uri, description);
            uri = null;
            description = null;
        }
    }

}

