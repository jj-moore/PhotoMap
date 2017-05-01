package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

class MapManager implements OnMapReadyCallback {
    private ActivityInterface activityInterface;
    private Context context;
    private GoogleMap map;
    private Marker currentMarker;
    private boolean animate;
    private boolean buildings;

    MapManager(Context context, ActivityInterface activityInterface) {
        this.animate = true;
        this.context = context;
        this.activityInterface = activityInterface;
    }

    // THIS IS WHERE THE APPLICATION REALLY STARTS
    // DO NOT ACCESS THE MAP UNTIL THIS IS CALLED
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setPadding(5, 5, 5, 5);
        map.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            public void onMapLongClick(LatLng location) {
                saveMap(location);
            }
        });

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int savedIndex = preferences.getInt("id", -1);
        setAnimateCamera(preferences.getBoolean("animate", true));
        setBuildings(preferences.getBoolean("buildings", true));
        activityInterface.mapToView(savedIndex);
    }

    // CALLED FROM MAIN ACTIVITY TO UPDATE MAP
    void updateMap(double lat, double lng, float zoom, float bearing, float tilt, int type, boolean marker) {
        CameraPosition.Builder position_builder = new CameraPosition.Builder();
        LatLng current_position = new LatLng(lat, lng);
        map.setBuildingsEnabled(buildings);

        map.clear();
        if (marker) {
            currentMarker = map.addMarker(new MarkerOptions().position(current_position));
        } else {
            currentMarker = null;
        }
        position_builder.target(current_position);
        position_builder.zoom(zoom);
        position_builder.bearing(bearing);
        position_builder.tilt(tilt);
        CameraPosition position = position_builder.build();
        CameraUpdate camera_update = CameraUpdateFactory.newCameraPosition(position);

        map.setMapType(type);
        if (animate) {
            map.animateCamera(camera_update);
        } else {
            map.moveCamera(camera_update);
        }

    }

    // UPDATE MAP WHEN NO LOCATION IS SET (E.G. NEW RECORD)
    void updateMap() {
        updateMap(0, 0, 0, 0, 0, GoogleMap.MAP_TYPE_NORMAL, false);
    }

    void setView(int index) {
        switch (index) {
            case 1:
                map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;
            case 2:
                map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                break;
            default:
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
        }
    }

    // CALLED FROM MAIN ACTIVITY WHEN HIT ENTER ON EDIT TEXT
    void searchMap(String search_string) {
        Geocoder geocoder = new Geocoder(context);
        try {
            List<Address> address = geocoder.getFromLocationName(search_string, 1);
            if (address.size() > 0) {
                this.updateMap(address.get(0).getLatitude(), address.get(0).getLongitude(), map.getCameraPosition().zoom,
                        map.getCameraPosition().bearing, map.getCameraPosition().tilt, map.getMapType(), true);
            }
        } catch (IOException e) {
            Log.d("Jeremy", "onKey: " + e.toString());
        }
    }

    // CALLED WHEN SELECTING 'SAVE MAP' ON OPTION MENU OR LONG PRESS ON MAP
    void saveMap(LatLng location) {
        CameraPosition position = map.getCameraPosition();
        if (location == null) {
            location = (currentMarker == null) ? position.target : currentMarker.getPosition();
        }

        map.clear();
        currentMarker = map.addMarker(new MarkerOptions().position(location));
        activityInterface.mapToDatabase(location.latitude, location.longitude,
                position.zoom, position.bearing, position.tilt, map.getMapType());
    }

    boolean getAnimateCamera() {
        return animate;
    }

    void setAnimateCamera(boolean animate) {
        this.animate = animate;
    }

    boolean hasBuildings() {
        return buildings;
    }

    void setBuildings(boolean buildings) {
        this.buildings = buildings;
        if (map != null) { // WHEN THIS IS CALLED BEFORE ON_MAP_READY
            map.setBuildingsEnabled(buildings);
        }
    }

}
