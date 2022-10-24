package com.opsc.map_io;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.opsc.map_io.databinding.ActivityMapsBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, TaskLoadedCallback {

    private static final float DEFAULT_ZOOM = 16;
    private static final String TAG = "Log";
    private static final int REQUEST_CODE = 1000;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location currentLocation;
    private Place targetLocation;
    private String distanceToDestination;
    private String estimatedTimeToDestination;
    private Polyline currentPolyline;
    private boolean initialLoad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initialize fusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // binding
        com.opsc.map_io.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //Current location camera reset
        binding.gpsRecenter.setOnClickListener(view -> getCurrentLocation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkMapServices()) {
            if (isLocationPermissionGranted()) {
                Log.i(TAG, "onResume: Running");
                if (initialLoad) {
                    moveCamera(targetLocation.getLatLng(), targetLocation.getName(), true);
                } else {
                    getCurrentLocation();
                }

            }
        }
    }

    private boolean checkMapServices() {
        if (isGoogleServiceAvailable()) {
            return isGPSEnabled();
        }
        return false;
    }


    private boolean isLocationPermissionGranted() {
        Log.i(TAG, "isLocationPermissionGranted: Checking for permissions");
        if (ActivityCompat.checkSelfPermission(MapsActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            //request permission
            ActivityCompat.requestPermissions(MapsActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
        }
        return false;
    }

    private boolean isGoogleServiceAvailable() {
        // check if google services is available
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MapsActivity.this);

        if (result == ConnectionResult.SUCCESS) {
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(result)) {
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MapsActivity.this, result, 2000);
            if (dialog != null) {
                dialog.show();
            }
        } else {
            Toast.makeText(this, "Google Services unavailable, Unable to open map", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("GPS")
                    .setMessage("GPS is required for application functionality. Please enable GPS.")
                    .setPositiveButton("Yes", ((dialogInterface, i) -> {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }))
                    .setCancelable(false)
                    .show();
            return false;
        }
        return true;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        }
    }

    private void getCurrentLocation() {
        @SuppressLint("MissingPermission") Task<Location> locationTask = fusedLocationProviderClient.getLastLocation();
        locationTask.addOnSuccessListener(location -> {
            if (location != null) {
                Log.i(TAG, "getCurrentLocation: " + location.getLatitude() + "," + location.getLongitude());
                currentLocation = location;

                // initial load
                initialLoad = true;

                // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(this);
                }

                // Initialize Places API
                placesAutoComplete();
            }
        });
    }

    private void placesAutoComplete() {
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.api_key));
        }

        // initialize places client
        Places.createClient(this);

        // initialize autocompleteSupportFragment
        AutocompleteSupportFragment autocompleteSupportFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        // location bias
        if (autocompleteSupportFragment != null) {
            autocompleteSupportFragment.setLocationBias(RectangularBounds.newInstance(
                    new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()),
                    new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude())
            ));
        }

        // Specify the types of place data to return.
        if (autocompleteSupportFragment != null) {
            autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        }

        // Set up a PlaceSelectionListener to handle the response.
        if (autocompleteSupportFragment != null) {
            autocompleteSupportFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    Log.i(TAG, "onPlaceSelected: Place " + place.getName() + ", " + place.getId());

                    //initialize targetLocation
                    targetLocation = place;

                    //show place
                    moveCamera(targetLocation.getLatLng(), targetLocation.getName(), true);

                    //get directions
                    getDirections(targetLocation);

                    //update infoAdapter
                    infoWindowUpdate();

                    //reset distanceToDestination & estimatedTimeToDestination
                    resetDestinationValues();
                }

                @Override
                public void onError(@NonNull Status status) {
                    Log.i(TAG, "onError: Error " + status);
                }
            });
        }

    }

    private void resetDestinationValues() {
        distanceToDestination = "N/A";
        estimatedTimeToDestination = "N/A";
    }

    @SuppressLint("PotentialBehaviorOverride")
    private void infoWindowUpdate() {
        InfoAdapter infoAdapter = new InfoAdapter(
                MapsActivity.this,
                distanceToDestination,
                estimatedTimeToDestination,
                targetLocation
        );

        try {
            mMap.setInfoWindowAdapter(infoAdapter);
        } catch (Exception e) {
            Log.i(TAG, "infoWindowUpdate: Exception " + e);
        }
    }

    private void getDirections(Place targetLocation) {
        String s = Uri.parse("https://maps.googleapis.com/maps/api/directions/json?")
                .buildUpon()
                .appendQueryParameter("origin", currentLocation.getLatitude() + "," + currentLocation.getLongitude())
                .appendQueryParameter("destination",
                        Objects.requireNonNull(targetLocation.getLatLng()).latitude + "," + Objects.requireNonNull(targetLocation.getLatLng()).longitude)
                .appendQueryParameter("mode", "driving")
                .appendQueryParameter("key", getString(R.string.api_key))
                .toString();

        Log.i(TAG, "getDirections: Directions URL " + s);

        try {
            s = new FetchURL(MapsActivity.this).execute(s, "driving").get();

            new JSONObject();
            JSONObject jsonObject;
            jsonObject = new JSONObject(s);

            JSONArray routes = jsonObject.getJSONArray("routes");
            JSONArray legs = ((JSONObject) routes.get(0)).getJSONArray("legs");

            //GET DISTANCE AND ESTIMATED TIME DIRECTIONS
            String distance = legs.getJSONObject(0).getJSONObject("distance").getString("text");
            String estTime = legs.getJSONObject(0).getJSONObject("duration").getString("text");

            //set values
            distanceToDestination = distance;
            estimatedTimeToDestination = estTime;
        } catch (ExecutionException | InterruptedException | JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        LatLng latLng = new LatLng(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        );

        // Move Camera
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));

        // Change mMap settings
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void moveCamera(LatLng latLng, String name, boolean addMarker) {
        // Clean map
        mMap.clear();

        // Move Camera
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));

        // Add marker
        if (addMarker) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(name);
            mMap.addMarker(markerOptions);
        }
    }

    @Override
    public void onTaskDone(Object... values) {
        if (currentPolyline != null)
            currentPolyline.remove();
        currentPolyline = mMap.addPolyline((PolylineOptions) values[0]);
    }
}