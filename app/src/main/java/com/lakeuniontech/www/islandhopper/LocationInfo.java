package com.lakeuniontech.www.islandhopper;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;

import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;


class LocationInfo {
    final private String MAPS_API_KEY = "AIzaSyAinxC0uPLKga4-J426lEDErUg8MjOO4L4";
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
            // TODO - look at what happens here on startup.  We need to get a quick fix for application
            // startup so that we can set the terminals.  But it's okay to delay on figuring out driving time.
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

    Terminal findClosestTerminal() {
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
        return closestTerminal;
    }

    void getDrivingTime() {
        if (currentLocation == null) {      // Should only be null if user has not granted permissions
            TextView textDrivingTime = (TextView) mainActivity.findViewById(R.id.textDrivingTime);
            textDrivingTime.setText("Estimated driving time:  Unable to access location");
            return;
        }

        if (currentLocation.getAccuracy() == 0  ||  currentLocation.getAccuracy() > MIN_ACCURACY)
            return;

        String url = String.format(Locale.US, URL_DIRECTIONS,
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                mainActivity.depart.terminal.latLong, MAPS_API_KEY);

        mainActivity.jsonRequest.sendRequest(url,
                new JsonRequestCallback() {
                    @Override
                    public void success(JSONObject response) {
                        try {
                            JSONArray routes = response.getJSONArray("routes");
                            JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
                            Integer duration = legs.getJSONObject(0).getJSONObject("duration").getInt("value");

                            duration = duration / 60;   // Convert to minutes

                            TextView textDrivingTime = (TextView) mainActivity.findViewById(R.id.textDrivingTime);
                            textDrivingTime.setText(String.format(Locale.US,
                                    "Estimated driving time:  %d minutes", duration));
                        } catch (Exception e) {
                            mainActivity.displayToast("Failed parsing directions");
                        }
                    }

                    @Override
                    public void success(JSONArray response) { }

                    @Override
                    public void failure(String error) {
                        mainActivity.displayToast("Request failed: " + error);
                    }
                });
    }
}
