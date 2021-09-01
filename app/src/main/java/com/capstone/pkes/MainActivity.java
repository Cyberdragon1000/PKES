package com.capstone.pkes;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.capstone.pkes.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import java.util.Set;

import static android.content.ContentValues.TAG;


public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    static byte[] salt = new byte[8];
    static byte[] IV = new byte[16];
    TextView t1,t2,t3,t4;// text views simply for viewing
    LocationManager locationManager;// for gps location
    SensorManager mSensorManager;// for sensor data
    private Sensor mAccelerometer;// for acceleration data
    private static final int PERMISSION_REQUEST_CODE = 100; //perm code, simply a number
    double latitude_key =0,longitude_key=0, latitude_car =Math.toRadians(60),longitude_car=Math.toRadians(60);

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermission();
        setContentView(R.layout.activity_main);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());



        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

        testing();


        activity_prediction.x = new ArrayList<>();
        activity_prediction.y = new ArrayList<>();
        activity_prediction.z = new ArrayList<>();

        activity_prediction.classifier = new TensorFlowClassifier(getApplicationContext());



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

    @SuppressLint("SetTextI18n")
    @Override
    public void onLocationChanged(Location loc) {
        latitude_key = Math.toRadians(loc.getLatitude());
        longitude_key=Math.toRadians(loc.getLongitude());
        double result= gps_distance.calculate_distance(latitude_key,longitude_key, latitude_car,longitude_car);
        t4.setText("\nlatitude:"+ latitude_key +"\nlongitude"+longitude_key+ "\ndistance :"+result);
    }


    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {
        float x1,y1,z1;
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            x1 = event.values[0];
            y1 = event.values[1];
            z1 = event.values[2];
            t1.setText("X : " + x1 + "Y : " + y1 + "Z : " + z1);
        }
        float[] results= activity_prediction.activityPrediction();
        if( results!=null) {
            t2.setText("  downstairs : " + activity_prediction.round(results[0]) +
                    "\n  jogging : " + activity_prediction.round(results[1]) +
                    "\n  sitting : " + activity_prediction.round(results[2]) +
                    "\n  standing : " + activity_prediction.round(results[3]) +
                    "\n  upstairs : " + activity_prediction.round(results[4]) +
                    "\n  walking : " + activity_prediction.round(results[5])
            );
        }
        activity_prediction.x.add(event.values[0]);
        activity_prediction.y.add(event.values[1]);
        activity_prediction.z.add(event.values[2]);
    }

    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    @SuppressLint("SetTextI18n")
    public void testing(){

            String key_text="the_password";//any key string
            dataencryption.random.nextBytes(salt);//salt for key gen
            dataencryption.random.nextBytes(IV);//iv for encryption

            byte[] key_of_128length= new byte[0];

            try {
                key_of_128length = dataencryption.create_hash_of_textkey(key_text, salt);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                e.printStackTrace();
            }

            byte[] key= dataencryption.generate_encryptionkey(key_of_128length);

            byte[] ciphered = dataencryption.encrypt(timestamp.gettimestamp(),key, IV);

            String deciphered= dataencryption.decrypt(ciphered,key, IV);

            t3.setText(deciphered+"\n"+ Arrays.toString(key) +"\n"+ Arrays.toString(ciphered)+"\n");



    }

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


    //=========================================================================================================


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
//        unregisterReceiver(mReceiver);
    }

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


}