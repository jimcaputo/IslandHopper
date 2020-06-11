package com.lakeuniontech.www.islandhopper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;


class LocationInfo {
    final private String URL_DIRECTIONS = "https://maps.googleapis.com/maps/api/directions/json?origin=%s,%s&destination=%s&key=%s";

    // Request location update every 1 minute
    private final long UPDATE_INTERVAL = 60 * 1000;
    // If accuracy is within 3 kilometer, then use the location update.  If worse, then wait for better
    private final float MIN_ACCURACY = 3000;
    // If location updates by more than 1 kilometer, retrieve new directions
    private final float DISTANCE_CHANGE = 1000;

    private MainActivity mainActivity;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation;
    private Location lastUpdateLocation;
    private Terminal lastUpdateTerminal;

    LocationInfo(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        locationManager = (LocationManager) mainActivity.getSystemService(Context.LOCATION_SERVICE);
    }

    void startListener() {
        // If locationListener was already created, typically on initial application launch, then no need
        // to create it again.
        if (locationListener != null)
            return;

        try {
            currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            locationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    currentLocation = location;
                    getDrivingTime();
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {}
                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
            };

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, UPDATE_INTERVAL, DISTANCE_CHANGE, locationListener);
        } catch (SecurityException e) {
            // Nothing to do if the user never granted permissions
        }

    }

    void stopListener() {
        try {
            if (locationListener != null) {
                locationManager.removeUpdates(locationListener);
                locationListener = null;
            }
        }
        catch (SecurityException e) {
            // Nothing to do if the user never granted permissions
        }
    }

    Terminal getDepartTerminal() {
        float shortestDistance = 0;
        Terminal closestTerminal = Terminal.ANACORTES;  // Default to Anacortes

        if (currentLocation == null)    // Should only be null if user has not granted permissions
            return closestTerminal;

        for (Terminal terminal : Terminal.values()) {
            Location terminalLocation = new Location("");
            terminalLocation.setLatitude(Double.parseDouble(terminal.latLong.substring(0, terminal.latLong.indexOf(','))));
            terminalLocation.setLongitude(Double.parseDouble(terminal.latLong.substring(terminal.latLong.indexOf(',') + 1)));

            if (shortestDistance == 0) {
                shortestDistance = currentLocation.distanceTo(terminalLocation);
                closestTerminal = terminal;
            }
            else if (currentLocation.distanceTo(terminalLocation) < shortestDistance) {
                shortestDistance = currentLocation.distanceTo(terminalLocation);
                closestTerminal = terminal;
            }
        }

        // HACK - optimized for just me. :) Since we default to Orcas
        if (closestTerminal != Terminal.ANACORTES)
            return Terminal.ORCAS;
        return Terminal.ANACORTES;
    }

    private String getHexSha1() {
        PackageInfo packageInfo = null;
        try {
            mainActivity.displayToast(mainActivity.getPackageName() + " " + mainActivity.getPackageManager().GET_SIGNING_CERTIFICATES);
            packageInfo = mainActivity.getPackageManager().getPackageInfo(
                    mainActivity.getPackageName(), mainActivity.getPackageManager().GET_SIGNING_CERTIFICATES);
            if (packageInfo == null
                    || packageInfo.signingInfo.getSigningCertificateHistory() == null
                    || packageInfo.signingInfo.getSigningCertificateHistory().length == 0
                    || packageInfo.signingInfo.getSigningCertificateHistory()[0] == null) {
                return null;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        String sha1;
        try {
            Signature[] signatures = packageInfo.signingInfo.getSigningCertificateHistory();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] digest = md.digest(signatures[0].toByteArray());

            char[] hexArray = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[digest.length * 2];
            for(int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                hexChars[i * 2] = hexArray[v >>> 4];
                hexChars[i * 2 + 1] = hexArray[v & 0x0F];
            }
            sha1 = new String(hexChars);
            mainActivity.displayToast(sha1);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return sha1;
    }

    void getDrivingTime() {
        if (currentLocation == null) {      // Should only be null if user has not granted permissions
            TextView textDrivingTime = (TextView) mainActivity.findViewById(R.id.textDrivingTime);
            textDrivingTime.setText("Estimated driving time:  Unable to access location");
            return;
        }

        if (currentLocation.getAccuracy() == 0  ||  currentLocation.getAccuracy() > MIN_ACCURACY)
            return;

        // Make sure something has changed since the last time we fetched directions
        if (currentLocation == lastUpdateLocation  &&  mainActivity.depart.terminal == lastUpdateTerminal)
            return;
        lastUpdateLocation = currentLocation;
        lastUpdateTerminal = mainActivity.depart.terminal;

        String url = String.format(Locale.US, URL_DIRECTIONS,
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                mainActivity.depart.terminal.latLong, ApiKeys.MAPS);


        HashMap<String, String> headers = new HashMap<String,String>();
        headers.put("X-Android-Package", mainActivity.getPackageName());
        String sha1 = getHexSha1();
        /*
        if (sha1 == null)
            return;
        headers.put("X-Android-Cert", sha1);
        */

        mainActivity.jsonRequest.sendRequest(url, new JsonRequestCallback() {
            @Override
            public void success(JSONObject response) {
                try {
                    JSONArray routes = response.getJSONArray("routes");
                    if (routes.length() == 0) {
                        mainActivity.displayToast("Unexpected Driving Time response: " + response);
                        return;
                    }
                    JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
                    int minutes = legs.getJSONObject(0).getJSONObject("duration").getInt("value");

                    minutes = minutes / 60;   // Convert to minutes
                    int days = minutes / (60 * 24);
                    minutes = minutes % (60 * 24);
                    int hours = minutes / 60;
                    minutes = minutes % 60;

                    String duration = "";
                    if (days > 0)  duration += String.format(Locale.US, "%d d ", days);
                    if (hours > 0)  duration += String.format(Locale.US, "%d h ", hours);
                    if (minutes > 0)  duration += String.format(Locale.US, "%d m", minutes);

                    TextView textDrivingTime = mainActivity.findViewById(R.id.textDrivingTime);
                    textDrivingTime.setText(String.format(Locale.US,
                            "Estimated driving time:  %s", duration));
                } catch (Exception e) {
                    mainActivity.displayToast("Failed parsing directions: " + e.toString() + "  "  + response);
                }
            }

            @Override
            public void failure(String error) {
                mainActivity.displayToast("Request failed: " + error);
            }
        });
    }
}
