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
import android.widget.*;
import app.akexorcist.gdaplibrary.GoogleDirection;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.*;
import learn2crack.asynctask.library.JSONParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String COUNTRY_NAME = "Slovenia";
    private static final float INITIAL_MAGNIFICATION = 8.0f;
    private static final LatLng INITIAL_NORTH_EAST_CORNER = new LatLng(46.8, 16.5);
    private static final LatLng INITIAL_SOUTH_WEST_CORNER = new LatLng(45.5, 13.5);

    private static final String JSON_SOURCE_URL = "http://opendata.si/promet/events/";
    private static final String JSON_KEY_LATITUDE = "y_wgs";
    private static final String JSON_KEY_LONGITUDE = "x_wgs";
    private static final String JSON_KEY_CAUSE = "vzrok";
    private static final String JSON_KEY_PRIORITY = "prioriteta";
    private static final String JSON_KEY_DESCRIPTION = "opis";
    private static final String JSON_KEY_EVENTS = "dogodki";
    private static final String JSON_KEY_EVENT_ARRAY = "dogodek";
    public static final double DISTANCE_THRESHOLD = 0.02;

    private Geocoder geocoder;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap map;
    private GoogleDirection direction;
    private DistanceCalculator distanceCalculator = new DistanceCalculator();

    private List<TrafficEvent> events = new ArrayList<TrafficEvent>();
    private List<Marker> eventMarkers = new ArrayList<Marker>();

    private Polyline routeLine;
    private Marker routeOrigin;
    private Marker routeDestination;

    private EditText originEditText;
    private EditText destinationEditText;
    private LinearLayout mapContainer;
    private TableLayout eventTable;
    private Button toggleButton;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        originEditText = (EditText) findViewById(R.id.origin);
        destinationEditText = (EditText) findViewById(R.id.destination);
        eventTable = (TableLayout) findViewById(R.id.table);
        mapContainer = (LinearLayout) findViewById(R.id.map_container);
        toggleButton = (Button) findViewById(R.id.button_toggle);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        geocoder = new Geocoder(getApplicationContext());
        LatLng initialLatLng = getLatLngFromString(COUNTRY_NAME);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng,INITIAL_MAGNIFICATION));
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                resetMapBounds(INITIAL_NORTH_EAST_CORNER,INITIAL_SOUTH_WEST_CORNER);
            }
        });

        new JSONParse().execute();

        direction = new GoogleDirection(this);
        direction.setOnDirectionResponseListener(new GoogleDirection.OnDirectionResponseListener() {
            public void onResponse(String status, Document doc, GoogleDirection dir) {
                flushDirections();
                flushEvents();
                processDirections(dir.getPolyline(doc,3,Color.YELLOW));
                processEvents(dir.getDirection(doc));
            }
        });
     }

    private void flushDirections() {
        if (routeLine == null) {
            toggleButton.setVisibility(View.VISIBLE);
        } else {
            routeLine.remove();
            routeOrigin.remove();
            routeDestination.remove();
            eventTable.removeAllViews();
            View headerView = getLayoutInflater().inflate(R.layout.event_header,null);
            eventTable.addView(headerView);
        }
    }

    private void flushEvents() {
        for (Marker marker : eventMarkers) {
            marker.remove();
        }
        eventMarkers.clear();
    }

    private void processDirections(PolylineOptions polylineOptions) {
        routeLine = map.addPolyline(polylineOptions);
        routeOrigin = map.addMarker(new MarkerOptions().position(origin)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN)));
        routeDestination = map.addMarker(new MarkerOptions().position(destination)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_BLUE)));
        resetMapBounds(routeOrigin.getPosition(),routeDestination.getPosition());
        }

    private void resetMapBounds(LatLng latLng1, LatLng latLng2) {
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        b.include(latLng1);
        b.include(latLng2);
        LatLngBounds bounds = b.build();
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds,100);
        map.animateCamera(cu);

    }

    private void processEvents(List<LatLng> latLngs) {
        LimitingRectangle rectangle = new LimitingRectangle(latLngs);
        for (TrafficEvent event : events) {
            boolean onRoute = false;
            if (rectangle.containsEvent(event)) {
                LatLng p = new LatLng(event.getLatitude(), event.getLongitude());
                for (int i = 0; i < latLngs.size() - 1; i++) {
                    LatLng v = latLngs.get(i);
                    LatLng w = latLngs.get(i + 1);
                    double distance = distanceCalculator.calculateDistanceFromPointToSegment(v, w, p);
                    if (distance < DISTANCE_THRESHOLD) {
                        onRoute = true;
                        break;
                    }
                }
            }
            showEvent(event,onRoute);
        }
    }

    private void updatePlaces() {
        String originString = originEditText.getText().toString() + ", " + COUNTRY_NAME;
        String destinationString = destinationEditText.getText().toString() + ", " + COUNTRY_NAME;
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

    public void toggleViews(View view) {
        if (eventTable.getVisibility() == View.VISIBLE) {
            eventTable.setVisibility(View.GONE);
            mapContainer.setVisibility(View.VISIBLE);
            toggleButton.setText("Table");
        } else {
            eventTable.setVisibility(View.VISIBLE);
            mapContainer.setVisibility(View.GONE);
            toggleButton.setText("Map");
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
            JSONObject json = jParser.getJSONFromUrl(JSON_SOURCE_URL);
            return json;
        }
        @Override
        protected void onPostExecute(JSONObject json) {
            pDialog.dismiss();
            try {
                JSONObject jsonEvents = json.getJSONObject(JSON_KEY_EVENTS);
                JSONArray jsonEventArray = jsonEvents.getJSONArray(JSON_KEY_EVENT_ARRAY);
                events = new ArrayList<TrafficEvent>();
                for (int i=0; i < jsonEventArray.length(); i++) {
                    JSONObject jsonEvent = jsonEventArray.getJSONObject(i);
                    TrafficEvent event = parseJSONToTrafficEvent(jsonEvent);
                    events.add(event);
                    showEvent(event,false);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private TrafficEvent parseJSONToTrafficEvent(JSONObject event) throws JSONException {
        double latitude = event.getDouble(JSON_KEY_LATITUDE);
        double longitude = event.getDouble(JSON_KEY_LONGITUDE);
        String cause = event.getString(JSON_KEY_CAUSE);
        int priority  = event.getInt(JSON_KEY_PRIORITY);
        String description = event.getString(JSON_KEY_DESCRIPTION);
        return new TrafficEvent(latitude,longitude,cause,priority,description);
    }

    private void showEvent(TrafficEvent event, boolean onRoute) {
        float hue = onRoute ? BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_VIOLET;
        LatLng latLng = new LatLng(event.getLatitude(),event.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
                .title(event.getCause() + " [PRIORITY=" + event.getPriority() + "]")
                .snippet(event.getDescription());
        Marker marker = map.addMarker(markerOptions);
        eventMarkers.add(marker);

        if (onRoute) {

            View rowView = getLayoutInflater().inflate(R.layout.event_row,null);

            TextView causeView = (TextView) rowView.findViewById(R.id.event_cause);
            causeView.setText(event.getCause());

            TextView priorityView = (TextView) rowView.findViewById(R.id.event_priority);
            priorityView.setText(String.valueOf(event.getPriority()));

            TextView descriptionView = (TextView) rowView.findViewById(R.id.event_description);
            descriptionView.setText(event.getDescription());

            eventTable.addView(rowView);

        }
    }

    public static class LimitingRectangle {

        public Double minLat;
        public Double maxLat;
        public Double minLng;
        public Double maxLng;

        public LimitingRectangle(List<LatLng> latLngs) {
            minLat = maxLat = minLng = maxLng = null;
            for (LatLng latLng : latLngs) {
                double lat = latLng.latitude;
                double lng = latLng.longitude;
                if (minLat == null) {
                    minLat = maxLat = lat;
                    minLng = maxLng = lng;
                } else {
                    minLat = lat < minLat ? lat : minLat;
                    maxLat = lat > maxLat ? lat : maxLat;
                    minLng = lng < minLng ? lng : minLng;
                    maxLng = lng > maxLng ? lng : maxLng;
                }
            }
            minLat -= DISTANCE_THRESHOLD;
            maxLat += DISTANCE_THRESHOLD;
            minLng -= DISTANCE_THRESHOLD;
            maxLng += DISTANCE_THRESHOLD;
        }

        public boolean containsEvent(TrafficEvent event) {
            return (event.getLatitude() > minLat
                    && event.getLatitude() < maxLat
                    && event.getLongitude() > minLng
                    && event.getLongitude() < maxLng);
        }

    }

}
