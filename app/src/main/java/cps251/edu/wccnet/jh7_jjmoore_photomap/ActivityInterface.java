package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.graphics.Bitmap;

interface ActivityInterface {

    void databaseToView(String operation, int result, String uri, String description, long date);

    void databaseToMap(double lat, double lng, float zoom, float bearing, float tilt, int type, boolean hasMarker);

    void mapToDatabase(double lat, double lng, float zoom, float bearing, float tilt, int type);

    void mapToView(int savedDbId);

    void removeDeletedRecord();

    void addImageToCache(String uri, Bitmap bitmap);

    void savePreferences(boolean liteMode, boolean animateCamera, boolean showBuildings);
}
