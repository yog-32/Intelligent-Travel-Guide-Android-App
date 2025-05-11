package com.example.travelguide2;

import static com.example.travelguide2.CONSTANTS.MAP_API_KEY;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText; // Import EditText
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.List;

public class BudgetActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mymap;
    private final int FINE_PERMISSION_CODE = 1;
    private Location currentLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private SearchView mapStart;
    private PlacesClient placesClient;
    private Address address;
    private EditText etNumberOfPeople; // Add EditText reference

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        mapStart = findViewById(R.id.mapStart);
        etNumberOfPeople = findViewById(R.id.etNumberOfPeople); // Initialize EditText

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.bmap);
        if (mapFragment == null) {
            mapFragment = new SupportMapFragment();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.bmap, mapFragment);
            transaction.commit();
        }
        mapFragment.getMapAsync(this);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), MAP_API_KEY);
        }
        placesClient = Places.createClient(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        getLastLocation(); // Request location early

        setupSearchViewListeners();

        // Check if intent contains place name (from Preference click)
        String placeNameFromIntent = getIntent().getStringExtra("place_name");
        Log.d("BudgetActivity", "Received place name: " + placeNameFromIntent);
        if (placeNameFromIntent != null && !placeNameFromIntent.isEmpty()) {
            mapStart.setQuery(placeNameFromIntent, true); // Triggers onQueryTextSubmit
        }
    }

    private void setupSearchViewListeners() {
        mapStart.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String location) {
                List<Address> addressList = null;

                // --- Get Number of People ---
                String numPeopleStr = etNumberOfPeople.getText().toString();
                int numberOfPeople = 0;
                if (TextUtils.isEmpty(numPeopleStr)) {
                    Toast.makeText(BudgetActivity.this, "Please enter the number of people", Toast.LENGTH_SHORT).show();
                    return true; // Indicate handled, prevent further processing
                }
                try {
                    numberOfPeople = Integer.parseInt(numPeopleStr);
                    if (numberOfPeople <= 0) {
                        Toast.makeText(BudgetActivity.this, "Number of people must be positive", Toast.LENGTH_SHORT).show();
                        return true; // Indicate handled
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(BudgetActivity.this, "Invalid number of people", Toast.LENGTH_SHORT).show();
                    return true; // Indicate handled
                }
                // --- End Get Number of People ---


                if (location != null && !location.isEmpty()) {
                    Geocoder geocoder = new Geocoder(BudgetActivity.this);
                    try {
                        // Ensure location is available before geocoding
                        if (currentLocation == null) {
                            Toast.makeText(BudgetActivity.this, "Current location not yet available. Please wait.", Toast.LENGTH_SHORT).show();
                            Log.e("BudgetActivity", "currentLocation is null in onQueryTextSubmit before geocoding");
                            // Optionally, try fetching location again here or disable search until location is ready
                            getLastLocation(); // Try to get location again
                            return true; // Handled, wait for location
                        }

                        addressList = geocoder.getFromLocationName(location, 1);
                        if (addressList != null && !addressList.isEmpty()) {
                            address = addressList.get(0);
                            LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                            MarkerOptions markerOptions = new MarkerOptions()
                                    .position(latLng)
                                    .title(location)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN));

                            if (mymap != null) {
                                mymap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
                                mymap.addMarker(markerOptions);
                            }

                            // Save search history
                            if (mAuth.getCurrentUser() != null) {
                                String userId = mAuth.getCurrentUser().getUid();
                                mDatabase.child("history").child(userId).child(location).setValue(true);
                            }

                            // --- Start BudgetShowActivity ---
                            Intent intent = new Intent(getApplicationContext(), BudgetShowActivity.class);
                            intent.putExtra("startlat", currentLocation.getLatitude());
                            intent.putExtra("startlon", currentLocation.getLongitude());
                            intent.putExtra("endlat", address.getLatitude());
                            intent.putExtra("endlon", address.getLongitude());
                            intent.putExtra("location", location);
                            intent.putExtra("numberOfPeople", numberOfPeople); // Pass number of people
                            startActivity(intent);
                            // --- End Start BudgetShowActivity ---

                        } else {
                            Toast.makeText(BudgetActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        Log.e("BudgetActivity", "Geocoding error", e);
                        Toast.makeText(BudgetActivity.this, "Error finding location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (IllegalStateException e) {
                        // Catch case where currentLocation might become null between check and use (less likely but possible)
                        Log.e("BudgetActivity", "IllegalStateException, currentLocation likely null", e);
                        Toast.makeText(BudgetActivity.this, "Current location error. Please try again.", Toast.LENGTH_SHORT).show();
                        getLastLocation(); // Try to refetch
                    }
                }
                return false; // Allow default behavior (e.g., close keyboard)
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    FINE_PERMISSION_CODE);
            return;
        }

        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, location -> { // Added 'this' for context clarity
                    if (location != null) {
                        currentLocation = location;
                        Log.d("BudgetActivity", "Current location obtained: " + location.getLatitude() + ", " + location.getLongitude());
                        // If map is ready, move camera to current location
                        if (mymap != null) {
                            LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                            mymap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15)); // Zoom level 15
                            // Optional: Add a marker for current location
                            // mymap.addMarker(new MarkerOptions().position(currentLatLng).title("Your Location"));
                        }
                    } else {
                        Log.e("BudgetActivity", "FusedLocationProvider returned null location.");
                        Toast.makeText(BudgetActivity.this, "Unable to get current location. Ensure location services are enabled.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(this, e -> { // Added failure listener
                    Log.e("BudgetActivity", "Error getting location", e);
                    Toast.makeText(BudgetActivity.this, "Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mymap = googleMap;
        // Optional: Enable My Location button if permissions granted
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mymap.setMyLocationEnabled(true);
            mymap.getUiSettings().setMyLocationButtonEnabled(true); // Show the button
            // If currentLocation was already fetched before map was ready, move camera now
            if (currentLocation != null) {
                LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                mymap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
            } else {
                // Attempt to get location again if map is ready but location isn't
                getLastLocation();
            }
        } else {
            // Handle case where permissions are not yet granted when map is ready
            // Consider requesting permissions again or informing the user
            Log.w("BudgetActivity", "Map ready but location permission not granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FINE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation(); // Get location now that permission is granted
                // Try enabling location features on map again
                if (mymap != null) {
                    try {
                        mymap.setMyLocationEnabled(true);
                        mymap.getUiSettings().setMyLocationButtonEnabled(true);
                    } catch (SecurityException se) {
                        Log.e("BudgetActivity", "SecurityException setting MyLocationEnabled", se);
                    }
                }
            } else {
                Toast.makeText(this, "Location permission is required for budget estimation based on current location.", Toast.LENGTH_LONG).show();
                // Handle denial - maybe disable features or explain necessity
            }
        }
    }
}
