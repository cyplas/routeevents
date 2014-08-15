package com.routeevents;
 
import android.annotation.TargetApi;
import android.app.Activity;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private static final LatLng SLOVENIA_CENTRE = new LatLng(46.151241,14.995463);
    private static final float SLOVENIA_MAGNIFICATION = 7.0f;
    private static final String SLOVENIA_ADDRESS_SUFFIX = ", Slovenia";
    private Geocoder geocoder;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap map;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        geocoder = new Geocoder(getApplicationContext());
        setContentView(R.layout.main_activity);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(SLOVENIA_CENTRE,SLOVENIA_MAGNIFICATION));
     }

    private void updatePlaces(String originString, String destinationString) {
        origin = getLatLngFromString(originString);
        destination = getLatLngFromString(destinationString);
        if (origin != null && destination != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, SLOVENIA_MAGNIFICATION + 2));
        }
    }

    private LatLng getLatLngFromString(String string) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(string + SLOVENIA_ADDRESS_SUFFIX, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                return new LatLng(address.getLatitude(), address.getLongitude());
            }
        }
        catch (IOException e) {
        }
        System.out.println("Route Events: couldn't find LatLng for string=" + string);
        return null;
    }

    public void search(View view) {
        String originString = "Ljubljana";
        String destinationString = "Maribor";
        updatePlaces(originString,destinationString);
    }

}
