package yadav.sourav.medvan;
import android.Manifest;
import android.app.AlertDialog;
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
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.util.Log;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,RoutingListener {

    private GoogleMap mMap;
    Location mLastLocation ;
    LocationRequest mLocationRequest;
    SupportMapFragment mapFragment;
    private LatLng myLocation;
    private Marker myMarker;

    private LinearLayout mCustomerInfo;
    private FirebaseAuth mAuth;
    private DatabaseReference mCustomerDatabase;

    private FusedLocationProviderClient mFusedLoactionClient;

    private Button mLogout,mSettings,mrideStatus,mHistory;

    private Switch mworkingSwitch;

    private int status = 0;

    private float rideDistance;

    private boolean isLoggingOut = false;

    private TextView mCustomerName,mCustomerPhone,mCustomerDestination;
    private String customerId="",destination;

    private LatLng destinationLatLng;
    private LatLng pendingRouteDestination = null; // Track pending route to draw

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        mFusedLoactionClient = LocationServices.getFusedLocationProviderClient(this);

        polylines = new ArrayList<>();
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mCustomerInfo = (LinearLayout) findViewById(R.id.customerInfo);

        mCustomerName= (TextView) findViewById(R.id.customerName);
        mCustomerPhone= (TextView) findViewById(R.id.customerPhone);
        mCustomerDestination= (TextView) findViewById(R.id.customerDestination);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }else {
            mapFragment.getMapAsync(this);
        }


        mSettings= (Button) findViewById(R.id.settings);
        mLogout= (Button) findViewById(R.id.logout);
        mrideStatus= (Button) findViewById(R.id.rideStatus);
        mworkingSwitch = (Switch) findViewById(R.id.workingSwitch);
        mHistory = (Button) findViewById(R.id.history);


        mworkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectDriver();
                }else{
                    disconnectDriver();
                }
            }
        });



        mrideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(status)
                {
                    case 1:
                        // If we have a pickup marker, try to draw route again
                        if (pickupMarker != null && mLastLocation != null) {
                            getRouteToMarker(pickupMarker.getPosition());
                            Toast.makeText(DriverMapActivity.this, "Redrawing route to patient...", Toast.LENGTH_SHORT).show();
                        }
                        
                        status = 2;
                        erasePolylines();
                        if(destinationLatLng != null && destinationLatLng.latitude != 0.0 && destinationLatLng.longitude != 0.0){
                            Log.d("DriverLocation", "Drawing route to destination: " + destinationLatLng.latitude + ", " + destinationLatLng.longitude);
                            getRouteToMarker(destinationLatLng);
                            mrideStatus.setText("Patient Picked Up! Heading to destination.");
                            Toast.makeText(DriverMapActivity.this, "Patient picked up! Showing route to destination.", Toast.LENGTH_LONG).show();
                        } else {
                            Log.e("DriverLocation", "Destination coordinates are invalid");
                            Toast.makeText(DriverMapActivity.this, "Error: Invalid destination coordinates", Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case 2:
                        recordRide();
                        endRide();
                        Toast.makeText(DriverMapActivity.this, "Ride completed! Returning to available status.", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        });


        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggingOut = true;
                disconnectDriver();

                FirebaseAuth.getInstance().signOut();
                Intent intent=new Intent(DriverMapActivity.this,Welcome_Activity.class);
                startActivity(intent);
                finish();
                return;
            }

        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(DriverMapActivity.this,DriverSettingActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Drivers");
                startActivity(intent);
                return;
            }
        });

        getAssignedCustomer();

    }


    private void endRide()
    {

        mrideStatus.setText("Available for Pickup");
        erasePolylines();
        
        // Remove pickup marker
        if(pickupMarker != null){
            pickupMarker.remove();
            pickupMarker = null;
        }
        
        // Hide customer info
        mCustomerInfo.setVisibility(View.GONE);
        
        // Reset status
        status = 0;
        
        // Clear pending route
        pendingRouteDestination = null;
        
        String userId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("customerRequest");
        driverRef.removeValue();


        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);
        customerId = "";
        rideDistance=0;

    }

    private void recordRide(){
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("History");
        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        HashMap map = new HashMap();
        map.put("driver", userId);
        map.put("customer", customerId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimestamp());
        map.put("destination", destination);
        map.put("location/from/lat", myLocation.latitude);
        map.put("location/from/lng", myLocation.longitude);
        map.put("location/to/lat", destinationLatLng.latitude);
        map.put("location/to/lng", destinationLatLng.longitude);
        map.put("distance", rideDistance);
        historyRef.child(requestId).updateChildren(map);
    }
    private Long getCurrentTimestamp() {
        Long timestamp= System.currentTimeMillis()/1000;
        return timestamp;
    }


    private void getAssignedCustomer(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    status = 1;
                    customerId = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickupLocation();
                    getAssignedCustomerDestination();
                    getAssignedCustomerInfo();

                }else{



                    endRide();
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void getAssignedCustomerInfo() {

        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase= FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);

        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("Name") != null) {

                        mCustomerName.setText(map.get("Name").toString());
                    }
                    if (map.get("Phone") != null) {

                        mCustomerPhone.setText(map.get("Phone").toString());
                    }

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedCustomerDestination(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");


        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    Map<String , Object> map = (Map<String , Object> ) dataSnapshot.getValue();
                    if(map.get("destination")!=null){
                        destination = map.get("destination").toString();
                        mCustomerDestination.setText("destination: "+destination);
                    }
                    else{
                        mCustomerDestination.setText("destination: ---");
                    }
                    double destinationLat = 0.0;
                    double destinationLng = 0.0;

                    if(map.get("destinationLat")!=null){
                        destinationLat= Double.valueOf(map.get("destinationLat").toString());
                    }

                    if(map.get("destinationLng")!=null){
                        destinationLng= Double.valueOf(map.get("destinationLng").toString());
                        destinationLatLng = new LatLng(destinationLat,destinationLng);
                    }

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    Marker pickupMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;
    private void getAssignedCustomerPickupLocation(){
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && !customerId.equals("")){
                    List <Object> map = (List<Object>) dataSnapshot.getValue();
                    double LocationLat = 0;
                    double LocationLng = 0;
                    if(map.get(0)!= null){
                        LocationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1)!= null){
                        LocationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng pickupLatLng = new LatLng(LocationLat, LocationLng);

                    // Remove existing pickup marker if any
                    if(pickupMarker != null){
                        pickupMarker.remove();
                    }
                    
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Patient Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.patient)));
                    
                    // Set pending route destination - will be drawn when location is available
                    pendingRouteDestination = pickupLatLng;
                    
                    // Try to draw route if location is already available
                    if (mLastLocation != null) {
                        getRouteToMarker(pickupLatLng);
                    } else {
                        Toast.makeText(DriverMapActivity.this, "Patient assigned! Waiting for location to show route...", Toast.LENGTH_LONG).show();
                    }
                    
                    // Update button text to indicate patient is assigned
                    mrideStatus.setText("Patient Assigned! Tap to start pickup.");
                    
                    // Show customer info
                    mCustomerInfo.setVisibility(View.VISIBLE);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


    }

    private void getRouteToMarker(LatLng pickupLatLng) {
        if (mLastLocation == null) {
            Log.e("DriverLocation", "Cannot draw route - mLastLocation is null");
            Toast.makeText(this, "Waiting for location update...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Log.d("DriverLocation", "Starting route calculation from: " + mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude() + " to: " + pickupLatLng.latitude + ", " + pickupLatLng.longitude);
            
            // Show loading message
            Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show();
            
            // Verify coordinates are valid
            if (pickupLatLng.latitude == 0.0 && pickupLatLng.longitude == 0.0) {
                Log.e("DriverLocation", "Invalid pickup coordinates: " + pickupLatLng.latitude + ", " + pickupLatLng.longitude);
                Toast.makeText(this, "Error: Invalid pickup location coordinates", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(false)
                    .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
                    .build();
            routing.execute();
            
            Log.d("DriverLocation", "Route calculation started successfully");
        } catch (Exception e) {
            Log.e("DriverLocation", "Error getting route: " + e.getMessage());
            Toast.makeText(this, "Error getting route: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }
    }

    private void updateRouteIfNeeded() {
        // Only update route if we have a customer assigned and are in pickup mode (status = 1)
        if (!customerId.equals("") && status == 1 && pickupMarker != null && mLastLocation != null) {
            LatLng pickupLocation = pickupMarker.getPosition();
            // Update route every 30 seconds or when driver moves significantly
            getRouteToMarker(pickupLocation);
        }
    }

    LocationCallback mLocationCallback = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!=null){
                    try {
                        if(!customerId.equals("") && mLastLocation!=null && location != null){
                            rideDistance += mLastLocation.distanceTo(location)/1000;
                        }
                        mLastLocation = location;

                        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                        myLocation = new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude());
                        
                        // Draw pending route if we have one and this is the first location update
                        if (pendingRouteDestination != null && status == 1) {
                            getRouteToMarker(pendingRouteDestination);
                            pendingRouteDestination = null; // Clear pending route
                        }
                        
                        if (myMarker != null) {
                            myMarker.remove();
                        }
                        
                        myMarker = mMap.addMarker(new MarkerOptions()
                            .position(myLocation)
                            .title("Your Ambulance")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ambulance)));

                        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
                        DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
                        GeoFire geoFireAvailable = new GeoFire(refAvailable);
                        GeoFire geoFireWorking = new GeoFire(refWorking);

                        Log.d("DriverLocation", "Updating location - Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                        Log.d("DriverLocation", "Driver ID: " + userId + ", Customer ID: " + customerId);

                        switch (customerId){
                            case "":
                                geoFireWorking.removeLocation(userId);
                                geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                                Log.d("DriverLocation", "Location updated in driversAvailable");
                                break;

                            default:
                                geoFireAvailable.removeLocation(userId);
                                geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                                Log.d("DriverLocation", "Location updated in driversWorking");
                                break;
                        }
                    } catch (Exception e) {
                        Log.e("DriverLocation", "Error updating location: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                new AlertDialog.Builder(this)
                        .setTitle("Please give permission...")
                        .setMessage("Please give permission...")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 1:{
                if(grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                        mFusedLoactionClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                }else {
                    Toast.makeText(getApplicationContext(),"Please provide the permission...",Toast.LENGTH_LONG).show();
                }
                break;
            }

        }
    }
    private void connectDriver(){
        try {
            checkLocationPermission();
            mFusedLoactionClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mMap.setMyLocationEnabled(true);
            Log.d("DriverStatus", "Driver connected and location updates started");
            
            // Verify driver is marked as available
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            
            if (mLastLocation != null) {
                geoFireAvailable.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                Log.d("DriverStatus", "Initial location set in driversAvailable");
            } else {
                Log.e("DriverStatus", "Cannot set initial location - mLastLocation is null");
            }
        } catch (Exception e) {
            Log.e("DriverStatus", "Error connecting driver: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error connecting to location services: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void disconnectDriver(){
        try {
            if(mFusedLoactionClient!=null){
                mFusedLoactionClient.removeLocationUpdates(mLocationCallback);
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable");

            GeoFire geoFire = new GeoFire(ref);
            geoFire.removeLocation(userId);
            Log.d("DriverStatus", "Driver disconnected successfully");
        } catch (Exception e) {
            Log.e("DriverStatus", "Error in disconnectDriver: " + e.getMessage());
        }
    }



    @Override
    protected void onStop() {
        super.onStop();
        if(!isLoggingOut){
            disconnectDriver();

        }
    }
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{android.graphics.Color.BLUE}; // Use blue color for better visibility
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(COLORS[colorIndex]);
            polyOptions.width(12); // Make the line thicker for better visibility
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            // Show route information to driver
            String routeInfo = "Route: " + route.get(i).getDistanceText() + " • " + route.get(i).getDurationText();
            Toast.makeText(getApplicationContext(), routeInfo, Toast.LENGTH_LONG).show();
            
            // Log route details
            Log.d("DriverLocation", "Route calculated - Distance: " + route.get(i).getDistanceText() + ", Duration: " + route.get(i).getDurationText());
        }
    }

    @Override
    public void onRoutingCancelled() {

    }

    private void erasePolylines(){
        for(Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
