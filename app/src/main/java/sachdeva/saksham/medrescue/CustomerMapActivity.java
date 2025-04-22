package sachdeva.saksham.medrescue;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.libraries.places.api.model.RectangularBounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private AutocompleteSupportFragment autocompleteFragment;
    private LocationCallback mLocationCallback;

    private FusedLocationProviderClient mFusedLocationClient;

    private Button mLogout,mRequest,mSettings,mHistory;
    private LatLng pickupLocation;
    private Boolean requestBol = false;
    private Marker pickupMarker;

    private SupportMapFragment mapFragment;

    private String destination;

    private LatLng destinationLatlng;

    private RatingBar mRatingBar;

    private LinearLayout mDriverInfo;

    private TextView mDriverName, mDriverPhone, mDriverCar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        
        // Initialize Places with proper configuration
        try {
            String apiKey = getString(R.string.google_maps_key);
            if (apiKey == null || apiKey.isEmpty()) {
                Log.e("Places", "Google Maps API key is missing");
                Toast.makeText(this, "Error: Google Maps API key is missing", Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.d("Places", "Initializing Places API with key: " + apiKey.substring(0, 10) + "...");
            
            // Force re-initialization of Places API
            if (Places.isInitialized()) {
                Log.d("Places", "Places API already initialized, reinitializing...");
            }
            
            Places.initialize(getApplicationContext(), apiKey);
            Log.d("Places", "Places API initialized successfully");
            
            // Verify initialization
            if (!Places.isInitialized()) {
                throw new Exception("Places API initialization failed");
            }
            
        } catch (Exception e) {
            Log.e("Places", "Error initializing Places API: " + e.getMessage());
            Toast.makeText(this, "Error initializing Places API: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment == null) {
            Log.e("Map", "Map fragment is null");
            Toast.makeText(this, "Error: Map fragment not found", Toast.LENGTH_LONG).show();
            return;
        }
        mapFragment.getMapAsync(this);

        destinationLatlng = new LatLng(0.0, 0.0);

        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);

        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhone = (TextView) findViewById(R.id.driverPhone);
        mDriverCar = (TextView) findViewById(R.id.driverCar);

        mRequest = (Button) findViewById(R.id.request);
        mSettings = (Button) findViewById(R.id.settings);
        mRatingBar = (RatingBar) findViewById(R.id.ratingBar);
        mHistory = (Button) findViewById(R.id.history);
        mLogout = (Button) findViewById(R.id.logout);

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, Welcome_Activity.class);
                startActivity(intent);
                finish();
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(requestBol){
                    endRide();
                }else {
                    // Check if location is available
                    if (mLastLocation == null) {
                        Toast.makeText(CustomerMapActivity.this, 
                            "Please wait for your location to be determined", 
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Check if destination is selected
                    if (destinationLatlng == null || 
                        (destinationLatlng.latitude == 0.0 && destinationLatlng.longitude == 0.0)) {
                        Toast.makeText(CustomerMapActivity.this, 
                            "Please select a destination first", 
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    try {
                        requestBol = true;
                        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                        GeoFire geoFire = new GeoFire(ref);
                        geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                        pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                        pickupMarker = mMap.addMarker(new MarkerOptions()
                            .position(pickupLocation)
                            .title("Pickup Here")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.patient)));

                        mRequest.setText("Getting Your Ambulance...");

                        getClosestDriver();
                    } catch (Exception e) {
                        Log.e("Request", "Error requesting ambulance: " + e.getMessage());
                        Toast.makeText(CustomerMapActivity.this, 
                            "Error requesting ambulance: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                        requestBol = false;
                    }
                }
            }
        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this,CustomerSettingsActivity.class);
                startActivity(intent);
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this,HistoryActivity.class);
                intent.putExtra("customerOrDriver","Customers");
                startActivity(intent);
            }
        });

        // Initialize the AutocompleteSupportFragment.
        autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        if (autocompleteFragment != null) {
            try {
                if (!Places.isInitialized()) {
                    throw new Exception("Places API not initialized");
                }

                // Set location bias to current location with 5km radius
                autocompleteFragment.setLocationBias(RectangularBounds.newInstance(
                    new LatLng(-90, -180),  // SW corner - will be updated when location is available
                    new LatLng(90, 180)     // NE corner - will be updated when location is available
                ));

                // Restrict results to hospitals only
                autocompleteFragment.setTypesFilter(Arrays.asList("hospital", "health"));
                
                // Set the search hint
                autocompleteFragment.setHint("Search for nearby hospitals");

                // Specify the types of place data to return.
                autocompleteFragment.setPlaceFields(Arrays.asList(
                    Place.Field.ID, 
                    Place.Field.NAME, 
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.TYPES,
                    Place.Field.RATING
                ));

                // Update location bias when user location changes
                mLocationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        for (Location location : locationResult.getLocations()) {
                            mLastLocation = location;
                            
                            // Update location bias to 5km around current location
                            if (autocompleteFragment != null && location != null) {
                                double lat = location.getLatitude();
                                double lng = location.getLongitude();
                                
                                // Calculate bounds for 5km radius
                                double radius = 5.0; // kilometers
                                double latChange = (radius / 111.0); // 1 degree = 111km approx.
                                double lngChange = (radius / (111.0 * Math.cos(Math.toRadians(lat))));
                                
                                LatLng sw = new LatLng(lat - latChange, lng - lngChange);
                                LatLng ne = new LatLng(lat + latChange, lng + lngChange);
                                
                                autocompleteFragment.setLocationBias(RectangularBounds.newInstance(sw, ne));
                            }

                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                            if(!getDriversAroundStarted)
                                getDriversAround();
                        }
                    }
                };

                // Set up a PlaceSelectionListener to handle the response.
                autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                    @Override
                    public void onPlaceSelected(@NonNull Place place) {
                        try {
                            if (!Places.isInitialized()) {
                                Toast.makeText(CustomerMapActivity.this, 
                                    "Places API not initialized. Please try again.", 
                                    Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Check if the selected place is a hospital
                            List<String> placeTypes = place.getPlaceTypes();
                            boolean isHospital = false;
                            if (placeTypes != null) {
                                for (String type : placeTypes) {
                                    if (type.equals("hospital") || 
                                        type.equals("health")) {
                                        isHospital = true;
                                        break;
                                    }
                                }
                            }

                            if (!isHospital) {
                                Toast.makeText(CustomerMapActivity.this,
                                    "Please select a hospital from the suggestions",
                                    Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Get the place details
                            destination = place.getName();
                            destinationLatlng = place.getLatLng();
                            
                            if (destinationLatlng != null) {
                                // Clear previous markers if any
                                if (pickupMarker != null) {
                                    pickupMarker.remove();
                                }
                                
                                // Add marker for selected hospital
                                pickupMarker = mMap.addMarker(new MarkerOptions()
                                    .position(destinationLatlng)
                                    .title(destination)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                                
                                // Move camera to selected location
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destinationLatlng, 15f));
                            }
                        } catch (Exception e) {
                            Log.e("Places", "Error selecting place: " + e.getMessage());
                            Toast.makeText(CustomerMapActivity.this,
                                "Error selecting place: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull Status status) {
                        String errorMessage = "Place selection failed";
                        if (status.getStatusMessage() != null) {
                            errorMessage += ": " + status.getStatusMessage();
                        }
                        Log.e("Places", errorMessage);
                        Toast.makeText(CustomerMapActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("Places", "Error setting up autocomplete: " + e.getMessage());
                Toast.makeText(this, 
                    "Error setting up location search: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("Places", "AutocompleteSupportFragment is null");
            Toast.makeText(this, "Search functionality is not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (autocompleteFragment != null) {
                getSupportFragmentManager().beginTransaction().remove(autocompleteFragment).commit();
                autocompleteFragment = null;
            }
        } catch (Exception e) {
            Log.e("Places", "Error cleaning up Places client: " + e.getMessage());
        }
    }

    private int radius=1;
    private Boolean driverFound = false;
    private String driverFoundID;

    GeoQuery geoQuery;


    private void getClosestDriver(){
        try {
            // Check if user is authenticated
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e("Request", "User not authenticated");
                Toast.makeText(CustomerMapActivity.this, 
                    "You must be logged in to request an ambulance", 
                    Toast.LENGTH_LONG).show();
                requestBol = false;
                mRequest.setText("Request An Ambulance");
                return;
            }
            
            String userId = auth.getCurrentUser().getUid();
            Log.d("Request", "User authenticated with ID: " + userId);
            
            // Check if location is available
            if (pickupLocation == null) {
                Log.e("Request", "Pickup location is null");
                Toast.makeText(CustomerMapActivity.this, 
                    "Unable to determine your location. Please try again.", 
                    Toast.LENGTH_LONG).show();
                requestBol = false;
                mRequest.setText("Request An Ambulance");
                return;
            }
            
            Log.d("Request", "Starting to find closest driver at location: " + 
                pickupLocation.latitude + ", " + pickupLocation.longitude);
            
            DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");
            
            // Check if the reference exists
            driverLocation.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Log.d("Request", "driversAvailable node exists: " + snapshot.exists());
                    if (!snapshot.exists()) {
                        Toast.makeText(CustomerMapActivity.this, 
                            "No drivers available in the system", 
                            Toast.LENGTH_LONG).show();
                        requestBol = false;
                        mRequest.setText("Request An Ambulance");
                        return;
                    }
                    
                    // Continue with GeoFire query
                    GeoFire geoFire = new GeoFire(driverLocation);
                    geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
                    geoQuery.removeAllListeners();

                    geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                        @Override
                        public void onKeyEntered(String key, GeoLocation location) {
                            Log.d("Request", "Driver found with key: " + key);
                            if (!driverFound && requestBol){
                                DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                                mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                                            if (driverFound){
                                                return;
                                            }

                                            driverFound = true;
                                            driverFoundID = dataSnapshot.getKey();
                                            Log.d("Request", "Selected driver ID: " + driverFoundID);

                                            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
                                            String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                            HashMap map = new HashMap();
                                            map.put("customerRideId", customerId);
                                            map.put("destination", destination);
                                            map.put("destinationLat", destinationLatlng.latitude);
                                            map.put("destinationLng", destinationLatlng.longitude);
                                            
                                            Log.d("Request", "Sending request to driver: " + driverFoundID);
                                            driverRef.updateChildren(map)
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d("Request", "Driver request sent successfully");
                                                    getDriverLocation();
                                                    getDriverInfo();
                                                    getHasRideEnded();
                                                    mRequest.setText("Looking for Driver's Location....");
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e("Request", "Failed to send driver request: " + e.getMessage());
                                                    Toast.makeText(CustomerMapActivity.this, 
                                                        "Failed to send request to driver: " + e.getMessage(), 
                                                        Toast.LENGTH_LONG).show();
                                                    driverFound = false;
                                                });
                                        }
                                    }
                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        Log.e("Request", "Database error: " + databaseError.getMessage());
                                        Toast.makeText(CustomerMapActivity.this, 
                                            "Database error: " + databaseError.getMessage(), 
                                            Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onKeyExited(String key) {
                            Log.d("Request", "Driver exited: " + key);
                        }

                        @Override
                        public void onKeyMoved(String key, GeoLocation location) {
                            Log.d("Request", "Driver moved: " + key);
                        }

                        @Override
                        public void onGeoQueryReady() {
                            Log.d("Request", "GeoQuery ready, driverFound: " + driverFound);
                            if (!driverFound) {
                                if (radius < 50) {
                                    Log.d("Request", "Increasing radius to: " + radius);
                                    radius++;
                                    getClosestDriver();
                                } else {
                                    runOnUiThread(() -> {
                                        Toast.makeText(CustomerMapActivity.this, 
                                            "No drivers found nearby. Please try again later.", 
                                            Toast.LENGTH_LONG).show();
                                        mRequest.setText("Request An Ambulance");
                                        requestBol = false;
                                        radius = 1;
                                    });
                                }
                            }
                        }

                        @Override
                        public void onGeoQueryError(DatabaseError error) {
                            Log.e("Request", "GeoQuery error: " + error.getMessage());
                            Toast.makeText(CustomerMapActivity.this, 
                                "Error finding drivers: " + error.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                            requestBol = false;
                            mRequest.setText("Request An Ambulance");
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Request", "Error accessing driversAvailable: " + error.getMessage());
                    Toast.makeText(CustomerMapActivity.this, 
                        "Error accessing driver database: " + error.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    requestBol = false;
                    mRequest.setText("Request An Ambulance");
                }
            });
        } catch (Exception e) {
            Log.e("Request", "Error in getClosestDriver: " + e.getMessage());
            Toast.makeText(CustomerMapActivity.this, 
                "Error finding drivers: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
            requestBol = false;
            mRequest.setText("Request An Ambulance");
        }
    }

    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;
    private void getDriverLocation(){
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(driverFoundID).child("l");
        driverLocationRefListener=driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestBol){
                    List<Object> map=(List<Object>) dataSnapshot.getValue();
                    double LocationLat = 0;
                    double LocationLng = 0;
                   // mRequest.setText("Ambulance Found");
                    if(map.get(0)!= null){
                        LocationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1)!= null){
                        LocationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(LocationLat, LocationLng);
                    if(mDriverMarker!=null){
                        mDriverMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);
                    if(distance<100){
                        mRequest.setText("Ambulance Arrived");
                    }else{
                        int dis = (int)distance/1000;
                        mRequest.setText("Ambulance Found: "+ String.valueOf(dis)+ " Kms away...");

                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your Ambulance").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ambulance)));
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }


    private void getDriverInfo(){
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    if(dataSnapshot.child("Name")!=null && dataSnapshot.child("Name").getValue()!=null){
                        mDriverName.setText(dataSnapshot.child("Name").getValue().toString());
                    }
                    if(dataSnapshot.child("Phone")!=null && dataSnapshot.child("Phone").getValue()!=null){
                        mDriverPhone.setText(dataSnapshot.child("Phone").getValue().toString());
                    }
                    if(dataSnapshot.child("Car")!=null && dataSnapshot.child("Car").getValue()!=null){
                        mDriverCar.setText(dataSnapshot.child("Car").getValue().toString());
                    }

                    int ratingSum = 0;
                    float ratingsTotal = 0;
                    float ratingsAvg = 0;
                    
                    if(dataSnapshot.child("rating").exists()) {
                        for (DataSnapshot child : dataSnapshot.child("rating").getChildren()){
                            if(child.getValue() != null) {
                                try {
                                    ratingSum = ratingSum + Integer.valueOf(child.getValue().toString());
                                    ratingsTotal++;
                                } catch (NumberFormatException e) {
                                    Log.e("Rating", "Error parsing rating value: " + e.getMessage());
                                }
                            }
                        }
                        if(ratingsTotal != 0){
                            ratingsAvg = ratingSum/ratingsTotal;
                            mRatingBar.setRating(ratingsAvg);
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("DriverInfo", "Error getting driver info: " + databaseError.getMessage());
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);

    }

    boolean getDriversAroundStarted = false;
    List<Marker> markers = new ArrayList<Marker>();
    private void getDriversAround(){
        getDriversAroundStarted = true;
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 999999999);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (key == null || location == null) {
                    Log.e("DriversAround", "Received null key or location");
                    return;
                }

                try {
                    LatLng driverLocation = new LatLng(location.latitude, location.longitude);
                    
                    // Check if marker already exists for this key
                    boolean markerExists = false;
                    for (Marker existingMarker : markers) {
                        Object tag = existingMarker.getTag();
                        if (tag != null && tag.toString().equals(key)) {
                            markerExists = true;
                            break;
                        }
                    }
                    
                    if (!markerExists) {
                        Marker mDriverMarker = mMap.addMarker(new MarkerOptions()
                            .position(driverLocation)
                            .title(key)
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ambulance)));
                            
                        if (mDriverMarker != null) {
                            mDriverMarker.setTag(key);
                            markers.add(mDriverMarker);
                        }
                    }
                } catch (Exception e) {
                    Log.e("DriversAround", "Error adding driver marker: " + e.getMessage());
                }
            }

            @Override
            public void onKeyExited(String key) {
                if (key == null) {
                    Log.e("DriversAround", "Received null key in onKeyExited");
                    return;
                }

                try {
                    for (Marker markerIt : markers) {
                        Object tag = markerIt.getTag();
                        if (tag != null && tag.toString().equals(key)) {
                            markerIt.remove();
                            markers.remove(markerIt);
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e("DriversAround", "Error removing driver marker: " + e.getMessage());
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                if (key == null || location == null) {
                    Log.e("DriversAround", "Received null key or location in onKeyMoved");
                    return;
                }

                try {
                    for (Marker markerIt : markers) {
                        Object tag = markerIt.getTag();
                        if (tag != null && tag.toString().equals(key)) {
                            markerIt.setPosition(new LatLng(location.latitude, location.longitude));
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e("DriversAround", "Error updating driver marker position: " + e.getMessage());
                }
            }

            @Override
            public void onGeoQueryReady() {
                Log.d("DriversAround", "GeoQuery ready");
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("DriversAround", "GeoQuery error: " + error.getMessage());
            }
        });
    }

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                new  android.app.AlertDialog.Builder(this)
                        .setTitle("Please give permission...")
                        .setMessage("Please give permission...")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission...", Toast.LENGTH_LONG).show();
                }
                break;
            }


        }}

        private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;
    private void getHasRideEnded(){
       // String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        driveHasEndedRef  = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest").child("customerRideId");
        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){


                }else{

                    endRide();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }
    private void endRide()
    {

        requestBol = false;
          if(geoQuery != null){
              geoQuery.removeAllListeners();
          }
          if(driverLocationRefListener != null&& driveHasEndedRefListener != null){
              driverLocationRef.removeEventListener(driverLocationRefListener);
              driveHasEndedRef.removeEventListener(driveHasEndedRefListener);
          }


        if(driverFoundID!=null){
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
            driverRef.removeValue();
            driverFoundID = null;

        }
        driverFound = false;
        radius = 1;

        String userId=FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
        if( pickupMarker!=null){
            pickupMarker.remove();
        }
        if (mDriverMarker != null){
            mDriverMarker.remove();
        }
        mRequest.setText("Request An Ambulance");

        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhone.setText("");

    }


   }