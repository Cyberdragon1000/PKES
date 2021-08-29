package com.capstone.pkes;

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
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    TextView t1,t2,t3;// text views simply for viewing
    LocationManager locationManager;// for gps location
    SensorManager mSensorManager;// for sensor data
    private Sensor mAccelerometer;// for acceleration data
    SecureRandom random = new SecureRandom();//RNG for iv and salt
    private static final int PERMISSION_REQUEST_CODE = 100; //perm code, simply a number
    double latitude_key =0,longitude_key=0, latitude_car =Math.toRadians(60),longitude_car=Math.toRadians(60);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        t1 = findViewById(R.id.justtext);
        t2 = findViewById(R.id.textView);
        t3 = findViewById(R.id.textView2);


        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

        checkPermission();

        testing();

    }

    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onLocationChanged(Location loc) {
        latitude_key = Math.toRadians(loc.getLatitude());
        longitude_key=Math.toRadians(loc.getLongitude());
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
            t3.setText("X : " + x1 + "Y : " + y1 + "Z : " + z1);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    @SuppressLint("SetTextI18n")
    public void testing(){
        String key_text="the_password";
        random.nextBytes(dataencryption.salt);//salt for key gen
        random.nextBytes(dataencryption.IV);//iv for encryption
        byte[] key_of_128length= new byte[0];
        try {
            key_of_128length = dataencryption.create_hash_of_textkey(key_text, dataencryption.salt);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        byte[] key= dataencryption.generate_encryptionkey(key_of_128length);

        byte[] ciphered = dataencryption.encrypt(timestamp.gettimestamp(),key, dataencryption.IV);
        String deciphered= dataencryption.decrypt(ciphered,key, dataencryption.IV);
        t1.setText(deciphered);
        t1.append("aa");

        double result= gpsdistance.calculate_distance(latitude_key,longitude_key, latitude_car,longitude_car);
        t1.setText("latitude:"+ latitude_key +"longitude"+longitude_key+ "results"+result);
    }





    private void checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_COARSE_LOCATION);

        if(!(result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED))
        {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
        }
    }

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


    private void showMessageOKCancel(DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage("You need to allow access to both the permissions")
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)/* comment this line and uncomment next for app to not work without permissions
                .setCancelable(false)*/
                .create()
                .show();
    }



}