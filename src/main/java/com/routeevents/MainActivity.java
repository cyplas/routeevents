package com.routeevents;
 
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
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
import learn2crack.asynctask.library.JSONParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private static final String COUNTRY_NAME = "Slovenia";
    private static final float INITIAL_MAGNIFICATION = 8.0f;
    private Geocoder geocoder;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap map;
    private GoogleDirection direction;

    private double eventLat;
    private double eventLng;
    private String eventCause;
    private int eventPriority;
    private String eventDescription;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        geocoder = new Geocoder(getApplicationContext());
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        LatLng initialLatLng = getLatLngFromString(COUNTRY_NAME);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng,INITIAL_MAGNIFICATION));
        new JSONParse().execute();

        direction = new GoogleDirection(this);
        direction.setOnDirectionResponseListener(new GoogleDirection.OnDirectionResponseListener() {
            public void onResponse(String status, Document doc, GoogleDirection dir) {
                map.addPolyline(dir.getPolyline(doc, 3, Color.YELLOW));
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
        String originString = ((EditText) findViewById(R.id.origin)).getText().toString() + ", " + COUNTRY_NAME;
        String destinationString = ((EditText) findViewById(R.id.destination)).getText().toString() + ", " + COUNTRY_NAME;
        origin = getLatLngFromString(originString);
        destination = getLatLngFromString(destinationString);
    }

    private LatLng getLatLngFromString(String string) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(string, 1);
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
        if (origin != null && destination != null) {
            direction.request(origin, destination, GoogleDirection.MODE_DRIVING);
        }
    }

    public void onPause() {
        super.onPause();
    }

    private class JSONParse extends AsyncTask<String, String, JSONObject> {
        private ProgressDialog pDialog;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage("Getting Data ...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }
        @Override
        protected JSONObject doInBackground(String... args) {
            JSONParser jParser = new JSONParser();
            JSONObject json = jParser.getJSONFromUrl("http://opendata.si/promet/events/");
            return json;
        }
        @Override
        protected void onPostExecute(JSONObject json) {
            pDialog.dismiss();
            try {
                JSONObject dogodki = json.getJSONObject("dogodki");
                JSONArray dogodekArray = dogodki.getJSONArray("dogodek");
                JSONObject dogodek = dogodekArray.getJSONObject(0);
                eventLat = dogodek.getDouble("y_wgs");
                eventLng = dogodek.getDouble("x_wgs");
                eventCause = dogodek.getString("vzrok");
                eventPriority = dogodek.getInt("prioriteta");
                eventDescription = dogodek.getString("opis");
                LatLng dogodekLatLng = new LatLng(eventLat,eventLng);
                map.addMarker(new MarkerOptions()
                        .position(dogodekLatLng)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .title(eventCause + " [" + eventPriority + "]")
                        .snippet(eventDescription));
                System.out.println("Route Events: " + eventLat + "/" + eventLng + "/" + eventDescription);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

}
