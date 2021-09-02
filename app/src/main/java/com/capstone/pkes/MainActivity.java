package com.capstone.pkes;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.capstone.pkes.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

/*
 *
 * PKES
 * ----
 *
 * Car registers for wake up
 * Phone advertises wth BLE
 *
 * Phone connects to car over Bluetooth
 *
 * Phone sends:
 *   - Encrypt(LOCATION_REQUEST, Timestamp(Self))
 *
 * Car verifies:
 *   - Timestamp(now) - Timestamp(Phone) <= 1 minute
 *
 * Car replies with:
 *   - Encrypt(LOCATION_RESPONSE, Timestamp, Location)
 *
 * Phone calculates own:
 *   - Timestamp
 *   - Location
 *   - Activity
 *
 * Phone verifies:
 *   - Timestamp(Self) - Timestamp(Car) <= 1 minute
 *   - Loc(Self) to Loc(Car) <= X meters
 *   - Activity is walking/jogging
 *
 * Phone sends:
 *   - Encrypt(UNLOCK_REQUEST, timestamp(self))
 *
 * Car verifies:
 *   - Timestamp(now) - Timestamp(Phone) <= 1 minute
 *
 * Car unlocks.
 * */

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    /////////////////////////////////////////////////////////////////////////////////////
    //==========================Encryption variables========================================
    ////////////////////////////////////////////////////////////////////////////////////
    String key=null,ciphered=null,deciphered_text=null;//var for storing key ,ciphered text and deciphered text
    //-----------------------------------------------------------------------------------


    /////////////////////////////////////////////////////////////////////////////////////
    //==========================GPS variables========================================
    ////////////////////////////////////////////////////////////////////////////////////
    LocationManager locationManager;// for gps location
    double latitude_key =0,longitude_key=0, latitude_car =Math.toRadians(60),longitude_car=Math.toRadians(60);
    double result_distance;
    //-----------------------------------------------------------------------------------


    /////////////////////////////////////////////////////////////////////////////////////
    //==========================ML prediction and accelerometer variables========================================
    ////////////////////////////////////////////////////////////////////////////////////
    SensorManager mSensorManager;// for sensor data
    private Sensor mAccelerometer;// for acceleration data
    float[] results_activity;
    //-----------------------------------------------------------------------------------


    /////////////////////////////////////////////////////////////////////////////////////
    //==========================Bluetooth variables========================================
    ////////////////////////////////////////////////////////////////////////////////////
    private AppBarConfiguration appBarConfiguration;
    //-----------------------------------------------------------------------------------


    /////////////////////////////////////////////////////////////////////////////////////
    //==========================Permissions variables========================================
    ////////////////////////////////////////////////////////////////////////////////////
    private static final int PERMISSION_REQUEST_CODE = 100; //perm code, simply a number
    //-----------------------------------------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ///////////////////////////////////////////////////////////////
        checkPermission();//check n req perms
        //////////////////////////////////////////////////////////////////////
        com.capstone.pkes.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());


        /////////////////////////////////////////////////////////////////////////////////////
        //==========================Sensor initialization========================================
        ////////////////////////////////////////////////////////////////////////////////////
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //-----------------------------------------------------------------------------------


        /////////////////////////////////////////////////////////////////////////////////////
        //==========================ML Prediction initialization========================================
        ////////////////////////////////////////////////////////////////////////////////////
        activity_prediction.x = new ArrayList<>();
        activity_prediction.y = new ArrayList<>();
        activity_prediction.z = new ArrayList<>();

        activity_prediction.classifier = new TensorFlowClassifier(getApplicationContext());
        //-----------------------------------------------------------------------------------


        /////////////////////////////////////////////////////////////////////////////////////
        //==========================GPS Initialization========================================
        ////////////////////////////////////////////////////////////////////////////////////
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
        //-----------------------------------------------------------------------------------


        testing();//just a func for testing vars
    }

    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }

    protected void onPause() {
        getSensorManager().unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        //        unregisterReceiver(mReceiver);
    }

    //GPS CO_ORDS AND DISTANCE CALC DONE HERE/////////////////////////////////////////////
    @Override
    public void onLocationChanged(Location loc) {
        //get key gps co-ords
        latitude_key  = Math.toRadians(loc.getLatitude());
        longitude_key = Math.toRadians(loc.getLongitude());

        //get distance calc
        result_distance = gps_distance.calculate_distance(latitude_key,longitude_key, latitude_car,longitude_car);
    }
    /////////////////////////////////////////////////////////////////////////////////


    //ACCELERATION DATA AND ML PREDICTION DONE HERE//////////////////////////////////
    @Override
    public void onSensorChanged(SensorEvent event) {

        //get acceleration values
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            activity_prediction.x.add(event.values[0]);
            activity_prediction.y.add(event.values[1]);
            activity_prediction.z.add(event.values[2]);
        }

        //prediction  results
        results_activity = activity_prediction.activityPrediction();
        //aif( results!=null) {
            //we have valid results to display so code}
    }
    //////////////////////////////////////////////////////////////////////////


    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }


///////////////////////////////////////PERMISSIONS REQUEST CODE//////////////////////////////////////////////////////////////////////

    //check if we got the perms :)
    private void checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_COARSE_LOCATION);

        if(!(result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED))
        {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
        }
    }

    //if perms not given do this
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean locationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean cameraAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                if (locationAccepted && cameraAccepted)
                    Toast.makeText(this, "Permission Granted, Now you can access location data and camera.", Toast.LENGTH_SHORT).show();
                else {
                    Toast.makeText(this, "Permission Denied, You cannot access location data and camera.", Toast.LENGTH_SHORT).show();
                    showMessageOKCancel(
                            (dialog, which) -> requestPermissions(new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION},
                                    PERMISSION_REQUEST_CODE));
                }
            }
        }
    }

    //dialog box for explaining perms
    private void showMessageOKCancel(DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("You need to allow access to both the permissions")
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)/* comment this line and uncomment next for app to not work without permissions
                .setCancelable(false)*/
                .create()
                .show();
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void testing(){

            try {
                key= data_encryption.generate_key();

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }


            ciphered = data_encryption.encrypt(timestamp.gettimestamp(),key);

            deciphered_text = data_encryption.decrypt(ciphered,key);

    }

    @NonNull
    private NavController getNavController() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (!(fragment instanceof NavHostFragment)) {
            throw new IllegalStateException("Activity " + this
                    + " does not have a NavHostFragment");
        }
        return ((NavHostFragment) fragment).getNavController();
    }

}