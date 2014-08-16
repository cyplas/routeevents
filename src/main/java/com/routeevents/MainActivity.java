package com.routeevents;
 
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private String addressSuffix;

    private Resources resources;

    private Geocoder geocoder;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap map;
    private GoogleDirection direction;
    private DistanceCalculator distanceCalculator = new DistanceCalculator();

    private Map<TrafficEvent,Boolean> eventMap = new HashMap<TrafficEvent,Boolean>();
    private List<Marker> eventMarkers = new ArrayList<Marker>();
    private boolean showAllEvents = true;

    private Polyline routeLine;
    private Marker routeOrigin;
    private Marker routeDestination;

    private EditText originEditText;
    private EditText destinationEditText;
    private LinearLayout mapContainer;
    private TableLayout eventTable;

    private MenuItem viewMenuItem;
    private MenuItem eventsMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        resources = getResources();

        addressSuffix = ", " + COUNTRY_NAME;
        originEditText = (EditText) findViewById(R.id.origin);
        destinationEditText = (EditText) findViewById(R.id.destination);
        eventTable = (TableLayout) findViewById(R.id.table);
        mapContainer = (LinearLayout) findViewById(R.id.map_container);
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        geocoder = new Geocoder(getApplicationContext());
        LatLng initialLatLng = getLatLngFromString(COUNTRY_NAME);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng,INITIAL_MAGNIFICATION));
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                resetMapBounds(INITIAL_NORTH_EAST_CORNER, INITIAL_SOUTH_WEST_CORNER);
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
                showEvents();
            }
        });
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        viewMenuItem = menu.findItem(R.id.menu_toggle_view);
        eventsMenuItem = menu.findItem(R.id.menu_toggle_events);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_view:
                toggleViews();
                return true;
            case R.id.menu_toggle_events:
                toggleEvents();
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    private void flushDirections() {
        if (routeLine == null) {
            viewMenuItem.setVisible(true);
            eventsMenuItem.setVisible(true);
        } else {
            routeLine.remove();
            routeOrigin.remove();
            routeDestination.remove();
        }
    }

    private void flushEvents() {
        for (Marker marker : eventMarkers) {
            marker.remove();
        }
        eventMarkers.clear();
        eventTable.removeAllViews();
        View headerView = getLayoutInflater().inflate(R.layout.event_header,null);
        eventTable.addView(headerView);
    }

    private void showEvents() {
        for (Map.Entry<TrafficEvent,Boolean> entry : eventMap.entrySet()) {
            if (showAllEvents || entry.getValue()) {
                showEvent(entry.getKey(),entry.getValue());
            }
        }
    }

    public void toggleEvents() {
        flushEvents();
        showAllEvents = !showAllEvents;
        showEvents();
        if (showAllEvents) {
            eventsMenuItem.setTitle(resources.getString(R.string.menuitem_events_route));
        } else {
            eventsMenuItem.setTitle(resources.getString(R.string.menuitem_events_all));
        }
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

    public float getDistanceThreshold() {
        return Float.parseFloat(PreferenceManager.getDefaultSharedPreferences(this).getString("pref_threshold",""));
    }

    private void processEvents(List<LatLng> latLngs) {
        float distanceThreshold = getDistanceThreshold();
        LimitingRectangle rectangle = new LimitingRectangle(latLngs,distanceThreshold);
        for (TrafficEvent event : eventMap.keySet()) {
            boolean onRoute = false;
            if (rectangle.containsEvent(event)) {
                LatLng p = new LatLng(event.getLatitude(), event.getLongitude());
                for (int i = 0; i < latLngs.size() - 1; i++) {
                    LatLng v = latLngs.get(i);
                    LatLng w = latLngs.get(i + 1);
                    double distance = distanceCalculator.calculateDistanceFromPointToSegment(v, w, p);
                    if (distance < distanceThreshold) {
                        onRoute = true;
                        break;
                    }
                }
            }
            eventMap.put(event, onRoute);
        }
    }

    private void updatePlaces() {
        String originString = originEditText.getText().toString() + addressSuffix;
        String destinationString = destinationEditText.getText().toString() + addressSuffix;
        updatePlaces(originString,destinationString);
    }

    private void updatePlaces(String originString, String destinationString) {
        origin = getLatLngFromString(originString);
        destination = getLatLngFromString(destinationString);
    }

    private LatLng getLatLngFromString(String string) {
        String warning = null;
        if (string.equals(addressSuffix)) {
            warning = resources.getString(R.string.message_empty_location);
        } else {
            try {
                List<Address> addresses = geocoder.getFromLocationName(string, 1);
                if (!addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    return new LatLng(address.getLatitude(), address.getLongitude());
                }
            } catch (IOException e) {
            }
            warning = resources.getString(R.string.message_unrecognised_location) + " " + string;
        }
        Toast toast = Toast.makeText(getApplicationContext(), warning, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.show();
        return null;
    }

    public void search(View view) {
        direction.setLogging(true);
        updatePlaces();
        if (origin != null && destination != null) {
            direction.request(origin, destination, GoogleDirection.MODE_DRIVING);
        }
    }

    public void toggleViews() {
        if (eventTable.getVisibility() == View.VISIBLE) {
            eventTable.setVisibility(View.GONE);
            mapContainer.setVisibility(View.VISIBLE);
            viewMenuItem.setTitle(R.string.menuitem_view_table);
            eventsMenuItem.setVisible(true);
        } else {
            eventTable.setVisibility(View.VISIBLE);
            mapContainer.setVisibility(View.GONE);
            viewMenuItem.setTitle(R.string.menuitem_view_map);
            eventsMenuItem.setVisible(false);
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
            pDialog.setMessage(resources.getString(R.string.message_fetching));
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
            if (json == null) {
                pDialog = new ProgressDialog(MainActivity.this);
                pDialog.setMessage(resources.getString(R.string.message_json_null));
                pDialog.setIndeterminate(false);
                pDialog.setCancelable(true);
                pDialog.show();
            } else {
                try {
                    JSONObject jsonEvents = json.getJSONObject(JSON_KEY_EVENTS);
                    JSONArray jsonEventArray = jsonEvents.getJSONArray(JSON_KEY_EVENT_ARRAY);
                    for (int i = 0; i < jsonEventArray.length(); i++) {
                        JSONObject jsonEvent = jsonEventArray.getJSONObject(i);
                        TrafficEvent event = parseJSONToTrafficEvent(jsonEvent);
                        eventMap.put(event, false);
                        showEvent(event, false);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
                .title(event.getCause() + " [" + resources.getString(R.string.label_priority) + "=" + event.getPriority() + "]")
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

        public LimitingRectangle(List<LatLng> latLngs, float distanceThreshold) {
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
            minLat -= distanceThreshold;
            maxLat += distanceThreshold;
            minLng -= distanceThreshold;
            maxLng += distanceThreshold;
        }

        public boolean containsEvent(TrafficEvent event) {
            return (event.getLatitude() > minLat
                    && event.getLatitude() < maxLat
                    && event.getLongitude() > minLng
                    && event.getLongitude() < maxLng);
        }

    }

}
