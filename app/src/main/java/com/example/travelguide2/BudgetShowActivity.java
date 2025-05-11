package com.example.travelguide2;

import static com.example.travelguide2.CONSTANTS.MAP_API_KEY;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode; // Required for synchronous network calls on main thread (BAD PRACTICE FOR PRODUCTION!)
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

// Imports for Google Maps Services (distance matrix and places)
import com.google.maps.DirectionsApi; // Using Directions might be better for real road distance
import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.NearbySearchRequest;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixRow;
// import com.google.maps.model.Duration; // Duration not directly used in cost
import com.google.maps.model.LatLng; // Use com.google.maps.model.LatLng for GeoApiContext
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import com.google.maps.model.PriceLevel; // Import PriceLevel if available and useful
import com.google.maps.model.RankBy; // RankBy might be useful
import com.google.maps.model.TravelMode; // Useful for different travel costs

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class BudgetShowActivity extends AppCompatActivity {

    private static final String TAG = "BudgetShowActivity";

    // --- Constants for Costs ---
    private static final double COST_PER_KM_CAR = 10.0;
    private static final double COST_PER_KM_BUS = 3.0;
    private static final double COST_PER_KM_TRAIN = 2.0;
    private static final double COST_PER_KM_CAB = 15.0;
    private static final int MEALS_PER_DAY = 3;
    private static final int NEARBY_SEARCH_RADIUS = 5000; // Increased radius (5km)
    private static final int MAX_PLACES_RESULTS = 20; // Max results per Nearby Search page

    // --- UI Elements ---
    private TextView tvTitle, tvLocation, tvDistance, tvPeople;
    private TextView tvTravelCostCar, tvTravelCostBus, tvTravelCostTrain, tvTravelCostCab;
    private TextView tvBasicBudget, tvBasicDetails;
    private TextView tvModerateBudget, tvModerateDetails;
    private TextView tvLuxuryBudget, tvLuxuryDetails;
    private Button btnStartNavigation;

    // --- Data ---
    private GeoApiContext geoApiContext;
    private double distanceInKm = 0.0;
    private int numberOfPeople = 1; // Default to 1
    private String destinationName = "Destination";
    private double endLat, endLon; // Store destination coordinates for navigation

    // Average Costs per Category
    private double avgStayBasic = 0, avgStayModerate = 0, avgStayLuxury = 0;
    private double avgFoodBasic = 0, avgFoodModerate = 0, avgFoodLuxury = 0;

    // Travel Costs
    private double travelCostCar = 0, travelCostBusTotal = 0, travelCostTrainTotal = 0, travelCostCab = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_show);

        // --- !!! WARNING: Synchronous Network Calls on Main Thread ---
        // This is BAD PRACTICE for production apps. Use AsyncTask, Coroutines, or Executors.
        // Added here to make the synchronous GeoApiContext calls work for simplicity.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // --- End Warning ---

        initializeUI();

        // Get data from Intent
        Intent intent = getIntent();
        double startLat = intent.getDoubleExtra("startlat", 0.0);
        double startLon = intent.getDoubleExtra("startlon", 0.0);
        endLat = intent.getDoubleExtra("endlat", 0.0); // Store globally
        endLon = intent.getDoubleExtra("endlon", 0.0); // Store globally
        destinationName = intent.getStringExtra("location"); // Get location name
        numberOfPeople = intent.getIntExtra("numberOfPeople", 1); // Get number of people

        // Update UI with initial info
        tvLocation.setText(String.format("Destination: %s", destinationName));
        tvPeople.setText(String.format(Locale.getDefault(),"For: %d People", numberOfPeople));


        // Initialize GeoApiContext
        geoApiContext = new GeoApiContext.Builder()
                .apiKey(MAP_API_KEY)
                .build();

        // --- Perform Calculations ---
        LatLng origin = new LatLng(startLat, startLon);
        LatLng destinationLatLng = new LatLng(endLat, endLon);

        // 1. Calculate Distance and Travel Costs
        calculateDistanceAndTravelCosts(origin, destinationLatLng);

        // 2. Search Nearby Hotels and Restaurants & Calculate Average Costs
        searchAndCalculatePlaceCosts(destinationLatLng);

        // 3. Calculate Final Budgets
        calculateTotalBudgets();

        // 4. Update UI with all results
        updateUIWithResults();

        // Setup Navigation Button
        setupNavigationButton();

    } // --- End onCreate ---

    private void initializeUI() {
        tvTitle = findViewById(R.id.tvTitle);
        tvLocation = findViewById(R.id.tvLocation);
        tvDistance = findViewById(R.id.tvDistance);
        tvPeople = findViewById(R.id.tvPeople);
        tvTravelCostCar = findViewById(R.id.tvTravelCostCar);
        tvTravelCostBus = findViewById(R.id.tvTravelCostBus);
        tvTravelCostTrain = findViewById(R.id.tvTravelCostTrain);
        tvTravelCostCab = findViewById(R.id.tvTravelCostCab);
        tvBasicBudget = findViewById(R.id.tvBasicBudget);
        tvBasicDetails = findViewById(R.id.tvBasicDetails);
        tvModerateBudget = findViewById(R.id.tvModerateBudget);
        tvModerateDetails = findViewById(R.id.tvModerateDetails);
        tvLuxuryBudget = findViewById(R.id.tvLuxuryBudget);
        tvLuxuryDetails = findViewById(R.id.tvLuxuryDetails);
        btnStartNavigation = findViewById(R.id.btnStartNavigation);
    }

    private void calculateDistanceAndTravelCosts(LatLng origin, LatLng destination) {
        try {
            // Using Distance Matrix API
            DistanceMatrix matrix = DistanceMatrixApi.newRequest(geoApiContext)
                    .origins(origin)
                    .destinations(destination)
                    .mode(TravelMode.DRIVING) // Specify mode, affects distance/duration
                    .await();

            if (matrix.rows.length > 0 && matrix.rows[0].elements.length > 0) {
                DistanceMatrixElement element = matrix.rows[0].elements[0];
                if (element.status == com.google.maps.model.DistanceMatrixElementStatus.OK) {
                    double distanceInMeters = element.distance.inMeters;
                    distanceInKm = distanceInMeters / 1000.0;

                    // Calculate base travel costs
                    travelCostCar = distanceInKm * COST_PER_KM_CAR;
                    double travelCostBusOnePerson = distanceInKm * COST_PER_KM_BUS;
                    double travelCostTrainOnePerson = distanceInKm * COST_PER_KM_TRAIN;
                    travelCostCab = distanceInKm * COST_PER_KM_CAB;

                    // Adjust for number of people for public transport
                    travelCostBusTotal = travelCostBusOnePerson * numberOfPeople;
                    travelCostTrainTotal = travelCostTrainOnePerson * numberOfPeople;

                    Log.d(TAG, String.format("Distance: %.2f km", distanceInKm));

                } else {
                    Log.e(TAG, "Distance Matrix Element Status: " + element.status);
                    Toast.makeText(this, "Could not calculate distance (" + element.status + ")", Toast.LENGTH_LONG).show();
                    tvDistance.setText("Distance: Error");
                }
            } else {
                Log.e(TAG, "Distance Matrix API returned no rows/elements.");
                Toast.makeText(this, "Error getting distance data", Toast.LENGTH_SHORT).show();
                tvDistance.setText("Distance: Error");
            }

        } catch (ApiException | InterruptedException | IOException e) {
            Log.e(TAG, "Error calculating distance: ", e);
            Toast.makeText(this, "Error calculating distance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            tvDistance.setText("Distance: Network Error");
        }
    }


    private void searchAndCalculatePlaceCosts(LatLng location) {
        // Search for Hotels (Lodging)
        List<PlacesSearchResult> hotels = searchNearby(location, PlaceType.LODGING);
        calculateAverageCosts(hotels, "hotel");

        // Search for Restaurants
        List<PlacesSearchResult> restaurants = searchNearby(location, PlaceType.RESTAURANT);
        calculateAverageCosts(restaurants, "food");
    }

    private List<PlacesSearchResult> searchNearby(LatLng location, PlaceType type) {
        List<PlacesSearchResult> resultsList = new ArrayList<>();
        try {
            PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, location)
                    .radius(NEARBY_SEARCH_RADIUS)
                    .type(type)
                    // .rankby(RankBy.PROMINENCE) // Default is PROMINENCE
                    .await(); // Limit is implicitly 20 unless paginating

            if (response.results != null && response.results.length > 0) {
                Log.d(TAG, "Found " + response.results.length + " results for type: " + type.name());
                for (PlacesSearchResult result : response.results) {
                    // Filter out places with no rating or very few ratings for better categorization
                    if (result.rating > 0 && result.userRatingsTotal > 5) { // Basic filter
                        resultsList.add(result);
                    }
                }
                Log.d(TAG, "Filtered to " + resultsList.size() + " results with ratings for type: " + type.name());
            } else {
                Log.w(TAG, "No results found for type: " + type.name());
            }
        } catch (ApiException | InterruptedException | IOException e) {
            Log.e(TAG, "Error searching nearby for " + type.name() + ": ", e);
            Toast.makeText(this, "Error searching for " + type.name() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        // Limit to MAX_PLACES_RESULTS just in case API returns more somehow or for consistency
        return resultsList.size() > MAX_PLACES_RESULTS ? resultsList.subList(0, MAX_PLACES_RESULTS) : resultsList;
    }

    private void calculateAverageCosts(List<PlacesSearchResult> places, String categoryType /* "hotel" or "food" */) {
        if (places == null || places.isEmpty()) {
            Log.w(TAG, "No places provided to calculate average costs for " + categoryType);
            return;
        }

        double totalCostBasic = 0, totalCostModerate = 0, totalCostLuxury = 0;
        int countBasic = 0, countModerate = 0, countLuxury = 0;

        for (PlacesSearchResult place : places) {
            Category category = categorizePlace(place);
            double estimatedCost = estimateCost(place, category, categoryType);

            switch (category) {
                case BASIC:
                    totalCostBasic += estimatedCost;
                    countBasic++;
                    break;
                case MODERATE:
                    totalCostModerate += estimatedCost;
                    countModerate++;
                    break;
                case LUXURY:
                    totalCostLuxury += estimatedCost;
                    countLuxury++;
                    break;
            }
        }

        // Calculate averages, handle division by zero
        if (categoryType.equals("hotel")) {
            avgStayBasic = (countBasic > 0) ? totalCostBasic / countBasic : estimateCost(null, Category.BASIC, "hotel"); // Use default if none found
            avgStayModerate = (countModerate > 0) ? totalCostModerate / countModerate : estimateCost(null, Category.MODERATE, "hotel");
            avgStayLuxury = (countLuxury > 0) ? totalCostLuxury / countLuxury : estimateCost(null, Category.LUXURY, "hotel");
            Log.d(TAG, String.format("Avg Stay - Basic: %.2f (%d), Mod: %.2f (%d), Lux: %.2f (%d)", avgStayBasic, countBasic, avgStayModerate, countModerate, avgStayLuxury, countLuxury));

        } else if (categoryType.equals("food")) {
            avgFoodBasic = (countBasic > 0) ? totalCostBasic / countBasic : estimateCost(null, Category.BASIC, "food");
            avgFoodModerate = (countModerate > 0) ? totalCostModerate / countModerate : estimateCost(null, Category.MODERATE, "food");
            avgFoodLuxury = (countLuxury > 0) ? totalCostLuxury / countLuxury : estimateCost(null, Category.LUXURY, "food");
            Log.d(TAG, String.format("Avg Food - Basic: %.2f (%d), Mod: %.2f (%d), Lux: %.2f (%d)", avgFoodBasic, countBasic, avgFoodModerate, countModerate, avgFoodLuxury, countLuxury));
        }
    }

    private enum Category { BASIC, MODERATE, LUXURY }

    private Category categorizePlace(PlacesSearchResult place) {
        // Simplified categorization based on rating and review count
        float rating = place.rating;
        int reviews = place.userRatingsTotal;

        // Thresholds (adjust as needed)
        if (rating >= 4.3 && reviews > 150) {
            return Category.LUXURY;
        } else if (rating >= 3.7 && reviews > 50) {
            return Category.MODERATE;
        } else {
            return Category.BASIC;
        }
        // Note: Google's PriceLevel enum exists but isn't always populated reliably in Nearby Search results.
        // if (place.priceLevel != null) { ... } could be an alternative/addition if available.
    }


    private double estimateCost(PlacesSearchResult place, Category category, String categoryType) {
        // Estimate cost based on category using random ranges
        // Using ThreadLocalRandom for better performance in concurrent scenarios (though not needed here)
        if (categoryType.equals("hotel")) { // Cost per night
            switch (category) {
                case BASIC:    return ThreadLocalRandom.current().nextDouble(800, 1800);
                case MODERATE: return ThreadLocalRandom.current().nextDouble(1800, 4000);
                case LUXURY:   return ThreadLocalRandom.current().nextDouble(4000, 10000); // Wider range for luxury
                default:       return 1500; // Default fallback
            }
        } else { // Cost per meal per person
            switch (category) {
                case BASIC:    return ThreadLocalRandom.current().nextDouble(100, 250);
                case MODERATE: return ThreadLocalRandom.current().nextDouble(250, 500);
                case LUXURY:   return ThreadLocalRandom.current().nextDouble(500, 1200);
                default:       return 200; // Default fallback
            }
        }
    }

    private void calculateTotalBudgets() {
        // Calculate total food cost per day per category
        double totalFoodBasic = avgFoodBasic * MEALS_PER_DAY * numberOfPeople;
        double totalFoodModerate = avgFoodModerate * MEALS_PER_DAY * numberOfPeople;
        double totalFoodLuxury = avgFoodLuxury * MEALS_PER_DAY * numberOfPeople;

        // Assign travel costs to budget levels (example assignment)
        double travelBasic = Math.min(travelCostBusTotal, travelCostTrainTotal); // Cheapest public
        double travelModerate = travelCostCar; // Assume car for moderate
        double travelLuxury = Math.max(travelCostCar, travelCostCab); // Most expensive private

        // Calculate total per day budget
        double totalBasic = avgStayBasic + totalFoodBasic + travelBasic;
        double totalModerate = avgStayModerate + totalFoodModerate + travelModerate;
        double totalLuxury = avgStayLuxury + totalFoodLuxury + travelLuxury;

        // Update UI (moved to separate function for clarity)
        updateUIWithBudgets(totalBasic, totalModerate, totalLuxury, travelBasic, travelModerate, travelLuxury);
    }

    private void updateUIWithResults() {
        // Update Distance
        tvDistance.setText(String.format(Locale.getDefault(),"Distance: %.1f km", distanceInKm));

        // Update Travel Costs Display
        tvTravelCostCar.setText(String.format(Locale.getDefault(),"Car: ₹%,.0f", travelCostCar));
        tvTravelCostBus.setText(String.format(Locale.getDefault(),"Bus: ₹%,.0f (Total for %d people)", travelCostBusTotal, numberOfPeople));
        tvTravelCostTrain.setText(String.format(Locale.getDefault(),"Train: ₹%,.0f (Total for %d people)", travelCostTrainTotal, numberOfPeople));
        tvTravelCostCab.setText(String.format(Locale.getDefault(),"Cab: ₹%,.0f", travelCostCab));
    }

    private void updateUIWithBudgets(double totalBasic, double totalModerate, double totalLuxury, double travelBasicCost, double travelModerateCost, double travelLuxuryCost) {
        // Update Budget Totals
        tvBasicBudget.setText(String.format(Locale.getDefault(),"Basic: ₹%,.0f", totalBasic));
        tvModerateBudget.setText(String.format(Locale.getDefault(),"Moderate: ₹%,.0f", totalModerate));
        tvLuxuryBudget.setText(String.format(Locale.getDefault(),"Luxury: ₹%,.0f", totalLuxury));

        // Update Budget Details
        tvBasicDetails.setText(String.format(Locale.getDefault(),
                "(Avg Stay: ₹%,.0f, Avg Food/Person/Day: ₹%,.0f, Travel: ₹%,.0f)",
                avgStayBasic, avgFoodBasic * MEALS_PER_DAY, travelBasicCost));
        tvModerateDetails.setText(String.format(Locale.getDefault(),
                "(Avg Stay: ₹%,.0f, Avg Food/Person/Day: ₹%,.0f, Travel: ₹%,.0f)",
                avgStayModerate, avgFoodModerate * MEALS_PER_DAY, travelModerateCost));
        tvLuxuryDetails.setText(String.format(Locale.getDefault(),
                "(Avg Stay: ₹%,.0f, Avg Food/Person/Day: ₹%,.0f, Travel: ₹%,.0f)",
                avgStayLuxury, avgFoodLuxury * MEALS_PER_DAY, travelLuxuryCost));
    }


    private void setupNavigationButton() {
        btnStartNavigation.setOnClickListener(v -> {
            // Create a Uri from an intent string. Use the result to create an Intent.
            // Geo URI format: "geo:latitude,longitude?q=query"
            // Using q=lat,lng focuses the map on the point. Using q=address tries to find the address.
            // Using just geo:lat,lng without q usually works too.
            Uri gmmIntentUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f", endLat, endLon));
            // Alternative: Uri gmmIntentUri = Uri.parse("geo:" + endLat + "," + endLon + "?q=" + Uri.encode(destinationName));


            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps"); // Ensure it opens in Google Maps

            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Toast.makeText(this, "Google Maps app not installed.", Toast.LENGTH_SHORT).show();
                // Fallback: Open in browser
                Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + endLat + "," + endLon);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
                startActivity(webIntent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown GeoApiContext to release resources (important!)
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
    }
}