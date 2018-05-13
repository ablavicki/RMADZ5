package com.ferit.ablavicki.gdjejeanamarija;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static android.widget.Toast.LENGTH_SHORT;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {


    public static final int CAMERA_REQUEST_CODE = 123;
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 321;
    private static final int IMAGE_GALLERY_REQUEST = 20;
    private GoogleMap mMap;
    private GeoDataClient mGeoDataClient;
    private PlaceDetectionClient mPlaceDetectionClient;
    private FusedLocationProviderClient mLocationProviderClient;
    private Location mLastKnownLocation;
    private CameraPosition mCameraPosition;
    String mCurrentPhotoPath;
    SoundPool mSoundPool;
    HashMap<Integer, Integer> mSoundMap = new HashMap<>();


    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    public static final String MSG_KEY = "message";
    private boolean mLocationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 17;
    private final LatLng mDefaultLocation = new LatLng(45.55111, 18.69389);
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    boolean mLoaded = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //provides access to Google's database of local place and business information
        mGeoDataClient = Places.getGeoDataClient(this, null);

        //provides quick access to the device's current place
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);
        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        loadSounds();

    }

    //checks if the user granted fine location permission and if not requests the permission
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    //handles the permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                invokeCamera();
            } else {
                Toast.makeText(this, R.string.cannotOpenCamera, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateLocationUI() {
        if (mMap == null) return;
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    //manipulates the map once it's ready
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMapClickListener(this);
        getLocationPermission();
        updateLocationUI();
        getDeviceLocation();
    }

    @Override
    public void onMapClick(LatLng latLng) {
        MarkerOptions markerOptions = new MarkerOptions();

        markerOptions.position(latLng);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        Location location = new Location("test");
        location.setLatitude(latLng.latitude);
        location.setLongitude(latLng.longitude);
        location.setTime(new Date().getTime());
        try {
            markerOptions.title(titleSetup(location));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Animating to the touched position
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        // Placing a marker on the touched position
        mMap.addMarker(markerOptions);
        if (mLoaded == false) return;
        else
            playSound(R.raw.boing);
    }

    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            try {
                                drawMarker(mLastKnownLocation);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void drawMarker(Location location) throws IOException {
        if (mMap != null) {
            mMap.clear();
            LatLng gps = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.addMarker(new MarkerOptions()
                    .position(gps)
                    .title(titleSetup(location)));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gps, 12));
        }
    }

    private String titleSetup(Location location) throws IOException {
        String message =
                "Lat: " + location.getLatitude() + "\nLon:" + location.getLongitude() + "\n";
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> nearByAddresses = geocoder.getFromLocation(
                        location.getLatitude(), location.getLongitude(), 1);
                if (nearByAddresses.size() > 0) {
                    StringBuilder stringBuilder = new StringBuilder();
                    Address nearestAddress = nearByAddresses.get(0);
                    stringBuilder.append(nearestAddress.getAddressLine(0)).append("\n")
                            .append(nearestAddress.getLocality()).append("\n")
                            .append(nearestAddress.getCountryName());
                    message = stringBuilder.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return message;
    }

    //menu setup
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.picture:
                takePhoto();
                //takePicture();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void takePhoto(){
        if ( checkSelfPermission(Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            invokeCamera();
        }
        else {
            String[] permissionRequest = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(permissionRequest, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void invokeCamera(){
        //get a file name
        File file = createImageFile();
        Uri pictureUri = FileProvider.getUriForFile(this, "com.ferit.ablavicki.gdjejeanamarija.fileprovider", file);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        //where to save images
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pictureUri);

        //request WRITE permission
        intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    private File createImageFile() {
        //the public picture directory
        File picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        File imageFile = new File(picturesDirectory, "picture" + timestamp + ".jpg");
        return imageFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE) {
                Toast.makeText(this, "Image Saved.", Toast.LENGTH_LONG).show();
                sendNotification();
            }
        }
    }

    private void sendNotification() {
        String message = createImageFile().getName();
        Intent notificationIntent = new Intent(this, MapsActivity.class);
        notificationIntent.putExtra(MSG_KEY, message);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(
                this,0,notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT
        );
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setAutoCancel(true)
                .setContentTitle("Image saved")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(notificationPendingIntent)
                .setLights(Color.BLUE, 2000, 1000)
                .setVibrate(new long[]{1000,1000,1000,1000,1000})
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        Notification notification = notificationBuilder.build();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);

        this.finish();
    }

    //marker sound setup
    private void loadSounds() {
        this.mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        this.mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                Log.d("Test", String.valueOf(sampleId));
                mLoaded = true;
            }
        });
        this.mSoundMap.put(R.raw.boing, this.mSoundPool.load(this, R.raw.boing, 1));
    }

    void playSound(int selectedSound) {
        int soundID = this.mSoundMap.get(selectedSound);
        this.mSoundPool.play(soundID, 1, 1, 1, 0, 1f);
    }
}

