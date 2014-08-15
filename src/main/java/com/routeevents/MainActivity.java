package com.routeevents;
 
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import app.akexorcist.gdaplibrary.GoogleDirection;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private static final LatLng SLOVENIA_CENTRE = new LatLng(41.151241,14.995463);
    private static final float SLOVENIA_MAGNIFICATION = 7.0f;
    private static final String SLOVENIA_ADDRESS_SUFFIX = ", Slovenia";
    private Geocoder geocoder;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap map;
    private GoogleDirection direction;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(SLOVENIA_CENTRE,SLOVENIA_MAGNIFICATION));

        geocoder = new Geocoder(getApplicationContext());
        direction = new GoogleDirection(this);
        direction.setOnDirectionResponseListener(new GoogleDirection.OnDirectionResponseListener() {
            public void onResponse(String status, Document doc, GoogleDirection dir) {
                map.addPolyline(dir.getPolyline(doc, 3, Color.RED));
                map.addMarker(new MarkerOptions().position(origin)
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN)));

                map.addMarker(new MarkerOptions().position(destination)
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_BLUE)));
            }
        });
     }

    private void updatePlaces() {
        String originString = ((EditText) findViewById(R.id.origin)).getText().toString();
        String destinationString = ((EditText) findViewById(R.id.destination)).getText().toString();
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
        direction.setLogging(true);
        updatePlaces();
        direction.request(origin, destination, GoogleDirection.MODE_DRIVING);
    }

    public void onPause() {
        super.onPause();
    }



}
