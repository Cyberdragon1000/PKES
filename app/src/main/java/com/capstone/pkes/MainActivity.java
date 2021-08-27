package com.capstone.pkes;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;


public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {
    TextView txtbox,t2,t3;
    LocationManager locationManager;
    SensorManager mSensorManager;
    private Sensor mAccelerometer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtbox = findViewById(R.id.justtext);
        t2 = findViewById(R.id.textView);
        t3 = findViewById(R.id.textView2);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        try {
            encrypt_text();
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onLocationChanged(Location loc) {
        txtbox.setText("lattitude:"+loc.getLatitude()+"longitude"+loc.getLongitude());
        double lati= Math.toRadians(loc.getLatitude());
        double longi=Math.toRadians(loc.getLongitude());
        double dlon = longi - 10;//random 2nd distance
        double dlat = lati - 11;//random 1st distance
        double a = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(11) * Math.cos(lati)
                * Math.pow(Math.sin(dlon / 2),2);

        double c = 2 * Math.asin(Math.sqrt(a));

        double r = 6371;//km

        double result =c * r;

    }

    public void encrypt_text () throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        String text="testing message";
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        SecretKey key = keygen.generateKey();
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] iv = cipher.getIV();

        Cipher decrypteddata = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decrypteddata.init(Cipher.DECRYPT_MODE, key);
        String decryptString = new String(decrypteddata.doFinal(ciphertext), StandardCharsets.UTF_8);
        t2.setText(decryptString);
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


}