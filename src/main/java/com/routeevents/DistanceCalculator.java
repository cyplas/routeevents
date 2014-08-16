package com.routeevents;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by cyp on 16.8.2014.
 */

/** Class which calculates the distance between a line segment and a point.
 * Ported to Java based on Grumdrig's reply here:
 * http://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment. */
public class DistanceCalculator {

    /** Calculate the distance from the point p to the ling segment between v and w. */
    public double calculateDistanceFromPointToSegment(LatLng v, LatLng w, LatLng p) {
        double l = calculateLengthSquared(v, w);
        if (l == 0) {
            return square(calculateDistance(v, p));
        } else {
            double t = calculateParameter(v,w,p,l);
            if (t < 0) {
                return calculateDistance(p, v);
            } else if (t > 1) {
                return calculateDistance(p, w);
            } else {
                LatLng s = calculateProjection(v,w,t);
                return calculateDistance(p,s);
            }
        }
    }

    private double square(double x) {
        return Math.pow(x,2.0);
    }

    private double calculateDistance(LatLng a, LatLng b) {
        double x = b.longitude - a.longitude;
        double y = b.latitude - a.latitude;
        return Math.sqrt(square(x) + square(y));
    }

    private double calculateLengthSquared(LatLng a, LatLng b) {
        double x = b.longitude - a.longitude;
        double y = b.latitude - a.longitude;
        return square(x) + square(y);
    }

    private double calculateParameter(LatLng v, LatLng w, LatLng p, double l) {
        double x = (p.longitude-v.longitude)*(w.longitude-v.longitude);
        double y = (p.latitude-v.latitude)*(w.latitude-v.latitude);
        return (x + y) / l;
    }

    private LatLng calculateProjection(LatLng v, LatLng w, double t) {
        double x = v.longitude + t * (w.longitude-v.longitude);
        double y = v.latitude + t * (w.latitude-v.latitude);
        return new LatLng(y,x);
    }

}
