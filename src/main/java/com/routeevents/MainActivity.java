package com.routeevents;
 
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
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

    // Parameters for initialisation
    private static final float INITIAL_MAGNIFICATION = 8.0f;
    private static final LatLng INITIAL_NORTH_EAST_CORNER = new LatLng(46.8, 16.5);
    private static final LatLng INITIAL_SOUTH_WEST_CORNER = new LatLng(45.5, 13.5);

    // URL and JSON parsing keys
    private static final String JSON_SOURCE_URL = "http://opendata.si/promet/events/";
    private static final String JSON_KEY_LATITUDE = "y_wgs";
    private static final String JSON_KEY_LONGITUDE = "x_wgs";
    private static final String JSON_KEY_CAUSE = "vzrok";
    private static final String JSON_KEY_PRIORITY = "prioriteta";
    private static final String JSON_KEY_DESCRIPTION = "opis";
    private static final String JSON_KEY_EVENTS = "dogodki";
    private static final String JSON_KEY_EVENT_ARRAY = "dogodek";

    // Address to append to user inputs before submitting to Google.
    private String addressSuffix;

    private Resources resources;
    private Geocoder geocoder;
    private GoogleMap map;
    private GoogleDirection direction;

    // Positions of places specified by users.
    private LatLng origin;
    private LatLng destination;

    // Used to determine whether events are on route.
    private DistanceCalculator distanceCalculator = new DistanceCalculator();

    // Map which holds events with a flag of whether they are on the route.
    private Map<TrafficEvent,Boolean> eventMap = new HashMap<TrafficEvent,Boolean>();

    // Markers currently on the Google Map.
    private List<Marker> eventMarkers = new ArrayList<Marker>();

    // Flag of whether to show all events on the map or just the ones on the route.
    private boolean showAllEvents = true;

    // List of route positions
    private ArrayList<LatLng> route = new ArrayList<LatLng>();

    // Route decorations
    private Polyline routeLine;
    private Marker routeOrigin;
    private Marker routeDestination;

    // Layout components
    private EditText originEditText;
    private EditText destinationEditText;
    private LinearLayout mapContainer;
    private TableLayout eventTable;

    // Menu items
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

        reparseAndRedrawEvents();

        direction = new GoogleDirection(this);
        direction.setOnDirectionResponseListener(new GoogleDirection.OnDirectionResponseListener() {
            public void onResponse(String status, Document doc, GoogleDirection dir) {
                flushDirections();
                showDirections(dir.getPolyline(doc, 3, Color.YELLOW));
                route = dir.getDirection(doc);
                if (getAutoRefetch()) {
                    reparseAndRedrawEvents();
                } else {
                    redrawEvents();
                }
            }
        });
     }

    private void reparseAndRedrawEvents() {
        new ParseAndRedrawEvents().execute();
    }

    private void redrawEvents() {
        flushEvents();
        identifyRouteEvents();
        showEvents();
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
            case R.id.menu_refetch:
                reparseAndRedrawEvents();
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    /** Erase previous direction decorations */
    private void flushDirections() {
        if (routeLine != null) {
            routeLine.remove();
            routeOrigin.remove();
            routeDestination.remove();
        }
    }

    /** Clear event map markers and table rows */
    private void flushEvents() {
        for (Marker marker : eventMarkers) {
            marker.remove();
        }
        eventMarkers.clear();
        eventTable.removeAllViews();
        View headerView = getLayoutInflater().inflate(R.layout.event_header,null);
        eventTable.addView(headerView);
    }

    /** Show events on map.
     * If showAllEvents is false, skip events which are not on the route.
     */
    private void showEvents() {
        for (Map.Entry<TrafficEvent,Boolean> entry : eventMap.entrySet()) {
            if (showAllEvents || entry.getValue()) {
                showEvent(entry.getKey(),entry.getValue());
            }
        }
    }

    /** Toggle between showing all events or just the route ones on the map. */
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

    /** Draw the new route and reset camera. */
    private void showDirections(PolylineOptions polylineOptions) {
        routeLine = map.addPolyline(polylineOptions);
        routeOrigin = map.addMarker(new MarkerOptions().position(origin)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN)));
        routeDestination = map.addMarker(new MarkerOptions().position(destination)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_BLUE)));
        resetMapBounds(routeOrigin.getPosition(),routeDestination.getPosition());
        viewMenuItem.setVisible(true);
        eventsMenuItem.setVisible(true);
        }

    /** Reset camera with appropriate centre and zoom to include specified positions. */
    private void resetMapBounds(LatLng latLng1, LatLng latLng2) {
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        b.include(latLng1);
        b.include(latLng2);
        LatLngBounds bounds = b.build();
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds,100);
        map.animateCamera(cu);

    }

    /** Get the user preference distance threshold which determines how close events have to be to one of a route's
     * segments to be on the route.
     */
    public float getDistanceThreshold() {
        return Float.parseFloat(PreferenceManager.getDefaultSharedPreferences(this).getString("pref_threshold", ""));
    }

    public boolean getAutoRefetch() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_auto_refetch", false);
    }


    /** Update the eventMap according to the new route and the distanceThreshold. */
    private void identifyRouteEvents() {
        if (!route.isEmpty()) {
            float distanceThreshold = getDistanceThreshold();
            LimitingRectangle rectangle = new LimitingRectangle(route, distanceThreshold);
            for (TrafficEvent event : eventMap.keySet()) {
                boolean onRoute = false;
                // bypass nested for loop if event is clearly not near any segment
                if (rectangle.containsEvent(event)) {
                    LatLng p = new LatLng(event.getLatitude(), event.getLongitude());
                    for (int i = 0; i < route.size() - 1; i++) {
                        LatLng v = route.get(i);
                        LatLng w = route.get(i + 1);
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
    }
    /** Update origin and destination positions based on user's input. */
    private void updatePlaces() {
        String originString = originEditText.getText().toString() + addressSuffix;
        String destinationString = destinationEditText.getText().toString() + addressSuffix;
        origin = getLatLngFromString(originString);
        destination = getLatLngFromString(destinationString);
    }

    /** Get LatLng of a place based on the String provided, using Geocoder.
     * Provide user with feedback messages for empty or invalid input. */
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

    /** If current origin and destination are valid, use Google Directions to determine path. */
    public void search(View view) {
        direction.setLogging(true);
        updatePlaces();
        if (origin != null && destination != null) {
            direction.request(origin, destination, GoogleDirection.MODE_DRIVING);
        }
    }

    /** Toggle between showing user a map or a table of the events. */
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

    public void onResume() {
        super.onResume();

    }

    /** AsyncTask in charge of parsing the TrafficEvents from JSON. */
    private class ParseAndRedrawEvents extends AsyncTask<String, String, JSONObject> {

        private ProgressDialog pDialog;

        /** Show message to start off. */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(MainActivity.this);
            pDialog.setMessage(resources.getString(R.string.message_fetching));
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }

        /** Get the JSON from the web service. */
        @Override
        protected JSONObject doInBackground(String... args) {
            JSONParser jParser = new JSONParser();
            JSONObject json = jParser.getJSONFromUrl(JSON_SOURCE_URL);
            return json;
        }

        /** If JSON is obtained successfully, update eventMap and show all the events. */
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
                    }
                    redrawEvents();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Parse the JSON event into a TrafficEvent. */
    private TrafficEvent parseJSONToTrafficEvent(JSONObject event) throws JSONException {
        double latitude = event.getDouble(JSON_KEY_LATITUDE);
        double longitude = event.getDouble(JSON_KEY_LONGITUDE);
        String cause = event.getString(JSON_KEY_CAUSE);
        int priority  = event.getInt(JSON_KEY_PRIORITY);
        String description = event.getString(JSON_KEY_DESCRIPTION);
        return new TrafficEvent(latitude,longitude,cause,priority,description);
    }

    /** Show the event in the app, differently depending on whether it's on the route.
     * If it's on the route, show on the map in red and include in the table list.
     * If it's not, then only show in purple on the map. */
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

    /** Helper class used to determine whether a TrafficEvent is potentially near a route.
     * The fields specify just beyond the far north, south, east and west edges of a rectangle
     * circumscribing the route. If the event is outside of that rectangle, then it's definitely
     * not near the route. "Just beyond" is defined by distanceThreshold.
     */
    public static class LimitingRectangle {

        public Double minLat;
        public Double maxLat;
        public Double minLng;
        public Double maxLng;

        /** Constructor which extracts the rectangle fields based on the route and the distanceThreshold. */
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

        /** Is the event potentially on the route? */
        public boolean containsEvent(TrafficEvent event) {
            return (event.getLatitude() > minLat
                    && event.getLatitude() < maxLat
                    && event.getLongitude() > minLng
                    && event.getLongitude() < maxLng);
        }

    }

}
