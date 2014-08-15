package com.routeevents;

/**
 * Created by cyp on 15.8.2014.
 */
public class TrafficEvent {

    final private double latitude;
    final private double longitude;
    final private String cause;
    final private int priority;
    final private String description;

    public TrafficEvent(double latitude, double longitude, String cause, int priority, String description) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.cause = cause;
        this.priority = priority;
        this.description = description;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCause() {
        return cause;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

}
