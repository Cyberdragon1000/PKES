package com.capstone.pkes;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.capstone.pkes.databinding.FragmentFirstBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class PhoneMode extends Fragment {

    private static final String TAG = "PKES-PhoneMode";

    private FragmentFirstBinding binding;

    private BluetoothDevice selectedDevice = null;
    ConnectThread mConnectThread;
    ConnectedThread mConnectedThread;
    Timer timer = null;
    Location currentLocation = null;
    Location carLoc = null;
    float[] activityPredictionResults = null;
    String predictedActivity = null;
    String carLocationData = null;
    int signalStrength = 0;

    final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    boolean scanning;
    Handler handler = new Handler();
    BluetoothGatt leGatt = null;

    final int MESSAGE_READ = 0;
    final int MESSAGE_WRITE = 1;
    final int MESSAGE_TOAST = 2;
    final int MESSAGE_STATE_CHANGE = 3;

    final String[] ACTIVITIES = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(view12 -> NavHostFragment.findNavController(PhoneMode.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment));

        binding.btnSelectOrScan.setOnClickListener(mSelectOrScanForCar);
        binding.btnConnect.setOnClickListener(mConnect);
        binding.btnUnlock.setOnClickListener(mUnlock);
//        binding.btnLock.setOnClickListener(view1 -> mConnectedThread.write("LOCK".getBytes()));
        binding.btnLock.setOnClickListener(view1 -> {
            if (leGatt == null) {
                Snackbar.make(binding.getRoot(), "Not connected yet.", Snackbar.LENGTH_SHORT).show();
                return;
            }
            writeToCar("A|3");
            Snackbar.make(binding.getRoot(), "Locking car.", Snackbar.LENGTH_SHORT).show();
        });

        binding.btnSendPing.setOnClickListener(view1 -> mConnectedThread.write("PING".getBytes()));

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(mReceiver, filter);

        // Get Location from main activity
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                getLocation();
                if (currentLocation != null) {
                    if (carLoc != null) {
                        Log.d(TAG, "timer.scheduleAtFixedRate: Car altitude: " + carLoc.getAltitude());
                        float distance = currentLocation.distanceTo(carLoc); // in meters
                        updateLocationText("Distance: " + distance + "m | Location: " + currentLocation.toString());

                        // Auto lock/unlock
//                        if (leGatt != null) {
//                            if (distance <= Constants.DISTANCE_RANGE) {
//                                long timestampNow = System.currentTimeMillis();
//                                String unlockRequestPayload = "A|" + Constants.ACTION_UNLOCK_REQUEST + "|" + timestampNow;
//                                writeToCar(unlockRequestPayload);
//                            } else {
//                                writeToCar("A|3");
//                            }
//                        }
                    } else {
                        updateLocationText("Location: " + currentLocation.toString());
                    }
                }
                getActivityPredictionResults();
                if (activityPredictionResults != null) {
                    updateActivityText("Activity: " + predictedActivity);
                }
                updateSignalStrength();
                updateSignalText("Signal: " + signalStrength);
            }
        },0,200);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
        binding = null;
        requireActivity().unregisterReceiver(mReceiver);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "mReceiver: ");
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                Log.i(TAG, "discovery: Device Name: " + deviceName);
                Log.i(TAG, "discovery: deviceHardwareAddress: " + deviceHardwareAddress);
                if (deviceName.contains(Constants.CAR_BT_DEVICE_NAME)) {
                    selectedDevice = device;
                    binding.tvDeviceInfo.setText("Selected Device: " + deviceName + " (" + deviceHardwareAddress + ")");
                    Log.d(TAG, "selected device ^");
                }
            }
        }
    };

    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    BluetoothDevice device = result.getDevice();
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address

                    Log.i(TAG, "leScanCallback discovery: Device Name: " + deviceName);
                    Log.i(TAG, "leScanCallback discovery: deviceHardwareAddress: " + deviceHardwareAddress);
                    if (deviceName != null && deviceName.contains(Constants.CAR_BT_DEVICE_NAME)) {
                        selectedDevice = device;
                        binding.tvDeviceInfo.setText("Selected Device: " + deviceName + " (" + deviceHardwareAddress + ")");
                        Log.d(TAG, "leScanCallback selected device ^");
                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                }
            };

    @SuppressLint("SetTextI18n")
    private final Button.OnClickListener mSelectOrScanForCar = arg0 -> {
        // Stops scanning after 10 seconds.
        final long SCAN_PERIOD = 5000;

        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "mSelectOrScanForCar: stopping scan for BLE device (before starting again)");
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            Log.d(TAG, "mSelectOrScanForCar: starting scan for BLE device");
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            Log.d(TAG, "mSelectOrScanForCar: stopping scan for BLE device");
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    };

    // Device scan callback.
    private BluetoothGattCallback leGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    Log.i(TAG, "leGattCallback onConnectionStateChange: gatt: " + gatt);
                    Log.i(TAG, "leGattCallback onConnectionStateChange: status: " + status);
                    Log.i(TAG, "leGattCallback onConnectionStateChange: newState: " + newState);

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.i(TAG, "Successfully connected to device");
                            leGatt = gatt;
                            leGatt.discoverServices();
                            updateConnectionStatus("Connected");
                            Snackbar.make(binding.getRoot(), "Connected!", Snackbar.LENGTH_SHORT).show();
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.i(TAG, "Successfully disconnected from device");
                            updateConnectionStatus("Not Connected");
                            gatt.close();
                        }
                    } else {
                        Log.i(TAG, "Error encountered for device! Disconnecting...");
                        gatt.close();
                    }
                }
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    Log.i(TAG, "leGattCallback onServicesDiscovered: gatt: " + gatt);
                    Log.i(TAG, "leGattCallback onServicesDiscovered: status: " + status);
                    Log.i(TAG, "leGattCallback onServicesDiscovered: services: " + gatt.getServices());

                    BluetoothGattService service = gatt.getService(UUID.fromString(Constants.BT_BLE_SERVICE_UUID));
                    Log.i(TAG, "leGattCallback onServicesDiscovered: service: " + service);
                    Log.i(TAG, "leGattCallback onServicesDiscovered: characteristics: " + service.getCharacteristics());

                    BluetoothGattCharacteristic char1 = service.getCharacteristic(UUID.fromString(Constants.BT_BLE_NOTIFY_UUID));

                    Log.i(TAG, "leGattCallback onServicesDiscovered: char1: " + char1);
                    Log.i(TAG, "leGattCallback onServicesDiscovered: char1 properties: " + char1.getProperties());
                    Log.i(TAG, "leGattCallback onServicesDiscovered: char1 permissions: " + char1.getPermissions());

                    gatt.readCharacteristic(char1);
                    gatt.setCharacteristicNotification(char1, true);
                    BluetoothGattDescriptor descriptor = char1.getDescriptors().get(0);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    Log.i(TAG, "leGattCallback onCharacteristicRead: gatt: " + gatt);
                    Log.i(TAG, "leGattCallback onCharacteristicRead: status: " + status);
                    Log.i(TAG, "leGattCallback onCharacteristicRead: characteristic: " + characteristic);

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "leGattCallback onCharacteristicRead: char read:" + characteristic.getValue());
                    } else {
                        Log.i(TAG, "Could not read characteristic.");
//                        gatt.close();
                    }
                }
                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    carLocationData = characteristic.getStringValue(0);
                    Log.i(TAG, "onCharacteristicChanged: " + carLocationData);

                    double lat, lng;
                    if (carLocationData.contains("|")) {
                        String[] carLocData = carLocationData.split("\\|");
                        lat = Float.parseFloat(carLocData[0]);
                        lng = Float.parseFloat(carLocData[1]);
                    } else {
                        lat = 0.0;
                        lng = 0.0;
                    }
                    carLoc = new Location("");
                    carLoc.setLatitude(lat);
                    carLoc.setLongitude(lng);
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                    super.onReadRemoteRssi(gatt, rssi, status);
                    signalStrength = rssi;
                }
            };

    private final Button.OnClickListener mConnect = arg0 -> {
        Log.d(TAG, "mConnect: starting discovery");
//        mConnectThread = new ConnectThread(selectedDevice);
//        mConnectThread.start();
        selectedDevice.connectGatt(getActivity(), false, leGattCallback);
    };

    private final Button.OnClickListener mUnlock = arg0 -> {
        Log.d(TAG, "mUnlock:");
        long timestampNow = System.currentTimeMillis();
//        String locationRequestPayload = "A|" + Constants.ACTION_LOCATION_REQUEST + "|" + timestampNow;
//        mConnectedThread.write(data_encryption.encrypt(locationRequestPayload).getBytes());

        if (leGatt == null) {
            Snackbar.make(binding.getRoot(), "Not connected yet.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        double lat, lng;
        if (carLocationData.contains("|")) {
            String[] carLocData = carLocationData.split("\\|");
            lat = Float.parseFloat(carLocData[0]);
            lng = Float.parseFloat(carLocData[1]);
        } else {
            lat = 0.0;
            lng = 0.0;
        }
        carLoc = new Location("");
        carLoc.setLatitude(lat);
        carLoc.setLongitude(lng);

        Location locHere = getLocation();

        float distance = -1;
        if (locHere != null) {
            distance = locHere.distanceTo(carLoc); // in meters
            Log.d(TAG, "unlock: current location: " + locHere);
            Log.d(TAG, "unlock: received location: " + carLoc);
            Log.d(TAG, "unlock: distance (meters): " + distance);
            if (distance > 500) {
                Snackbar.make(binding.getRoot(), "Car too far: " + distance + "m",
                        Snackbar.LENGTH_SHORT).show();
                return;
            };
        }

        // TODO: Activity recognition
//        if (predictedActivity != null) {
//            String[] whitelistedActivities = {"Jogging", "Standing", "Walking"};
//            if (!Arrays.asList(whitelistedActivities).contains(predictedActivity)) {
//                Snackbar.make(binding.getRoot(), "Unlock not allowed during " + predictedActivity,
//                        Snackbar.LENGTH_SHORT).show();
//                return;
//            };
//        }

        String unlockRequestPayload = "A|" + Constants.ACTION_UNLOCK_REQUEST + "|" + timestampNow;
        writeToCar(unlockRequestPayload);
        Snackbar.make(binding.getRoot(), "Unlocking car.", Snackbar.LENGTH_SHORT).show();
    };

    public final void writeToCar(String msg) {
//        msg = data_encryption.encrypt(msg);

        BluetoothGattCharacteristic writeChar = leGatt.getService(UUID.fromString(Constants.BT_BLE_SERVICE_UUID)).getCharacteristic(UUID.fromString(Constants.BT_BLE_WRITE_UUID));
        writeChar.setValue(msg);
        leGatt.writeCharacteristic(writeChar);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(Constants.BT_SERVICE_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            mConnectedThread = new ConnectedThread(mmSocket, mHandler);
            mConnectedThread.start();

            updateConnectionStatus("Connected");
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
                updateConnectionStatus("Not Connected");
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @SuppressLint("HandlerLeak")
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ConnectedThread.STATE_CONNECTED:
                            Log.d(TAG, "handleMessage: Connected!");
                            updateConnectionStatus("Connected");
                            break;
                        case ConnectedThread.STATE_CONNECTING:
                            Log.d(TAG, "handleMessage: Connecting...");
                            break;
                        case ConnectedThread.STATE_LISTEN:
                        case ConnectedThread.STATE_NONE:
                            Log.d(TAG, "handleMessage: Not connected (anymore?)!");
                            updateConnectionStatus("Not Connected");
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d(TAG, "handleMessage: read: " + readMessage);

                    switch (readMessage) {
                        case "PING":
                            Snackbar.make(binding.getRoot(), "Received Ping",
                                    Snackbar.LENGTH_SHORT).show();
                            mConnectedThread.write("PONG".getBytes());
                            return;
                        case "PONG":
                            Snackbar.make(binding.getRoot(), "Received Ping Reply",
                                    Snackbar.LENGTH_SHORT).show();
                            return;
                    }

                    String[] actionPayload = data_encryption.decrypt(readMessage).split("\\|");
                    int action = Integer.parseInt(actionPayload[1]);
                    long timestamp = Long.parseLong(actionPayload[2]);

                    switch (action) {
                        case Constants.ACTION_LOCATION_RESPONSE:
                            Log.d(TAG, "action: Location Response: " + actionPayload);
                            double lat = Float.parseFloat(actionPayload[3]);
                            double lng = Float.parseFloat(actionPayload[4]);
                            Location loc = new Location("");
                            loc.setLatitude(lat);
                            loc.setLongitude(lng);
                            Location locHere = getLocation();
                            float distance = locHere.distanceTo(loc); // in meters
                            Log.d(TAG, "action: Location Response: current location: " + locHere);
                            Log.d(TAG, "action: Location Response: received location: " + loc);
                            Log.d(TAG, "action: Location Response: distance (meters): " + distance);

                            if (distance > 50) break;

                            long timestampNow = System.currentTimeMillis();
                            if ((timestampNow - timestamp) > 1*60*1e3) break;

                            // TODO: Activity recognition

                            String unlockRequestPayload = "A|" + Constants.ACTION_UNLOCK_REQUEST + "|" + timestampNow;
                            mConnectedThread.write(data_encryption.encrypt(unlockRequestPayload).getBytes());
                            break;
                        default:
                            Log.d(TAG, "action: Unknown action: " + actionPayload);
                            break;
                    }
                    break;
                case MESSAGE_TOAST:
                    Log.d(TAG, "handleMessage: toast: " + msg.getData().getString("toast"));
                    break;
            }
        }
    };

    private void updateConnectionStatus(String text) {
        new Handler(Looper.getMainLooper()).post(() -> binding.tvConnectionStatus.setText(text));
    }

    private Location getLocation() {
        currentLocation = ((MainActivity) getActivity()).getLocation();
        if (currentLocation != null) {
//            currentLocation.setAltitude(0);
        }
        return currentLocation;
//        return new Location("");
    }

    private float[] getActivityPredictionResults() {
        activityPredictionResults = ((MainActivity) getActivity()).getActivityPredictionResults();
        if (activityPredictionResults != null) {
            predictedActivity = ACTIVITIES[maxIndex(activityPredictionResults)];
        }
        return activityPredictionResults;
//        return new float[]{1.0F, 2.2F, 4.5F,12,12, 0.1F};
    }

    private void updateSignalStrength() {
        if (leGatt != null) {
            leGatt.readRemoteRssi();
        }
    }

    private void updateLocationText(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (binding != null) binding.tvLocation.setText(text);
        });
    }

    private void updateActivityText(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
//            if (binding != null) binding.tvActivity.setText(text);
            if (binding != null) binding.tvActivity.setText("Activity: standing");
        });
    }

    private void updateSignalText(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (binding != null) binding.tvSignal.setText(text);
        });
    }

    public static int maxIndex(float[] list) {
        int i=0, maxIndex=-1;
        Float max=null;
        for (Float x : list) {
            if ((x != null) && ((max==null) || (x>max))) {
                max = x;
                maxIndex = i;
            }
            i++;
        }
        return maxIndex;
    }
}