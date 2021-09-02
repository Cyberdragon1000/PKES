package com.capstone.pkes;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.Set;
import java.util.UUID;

public class PhoneMode extends Fragment {

    private static final String TAG = "PKES-PhoneMode";

    private static final String CAR_BT_DEVICE_NAME = "Nexus";

    private FragmentFirstBinding binding;

    private BluetoothDevice selectedDevice = null;
    ConnectThread mConnectThread;
    ConnectedThread mConnectedThread;

    final int MESSAGE_READ = 0;
    final int MESSAGE_WRITE = 1;
    final int MESSAGE_TOAST = 2;
    final int MESSAGE_STATE_CHANGE = 3;

    String mConnectedDeviceName;

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

        binding.btnSendPing.setOnClickListener(view1 -> mConnectedThread.write("PING".getBytes()));

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
                if (deviceName.contains(CAR_BT_DEVICE_NAME)) {
                    selectedDevice = device;
                    binding.tvDeviceInfo.setText("Selected Device: " + deviceName + " (" + deviceHardwareAddress + ")");
                    Log.d(TAG, "selected device ^");
                }
            }
        }
    };

    @SuppressLint("SetTextI18n")
    private final Button.OnClickListener mSelectOrScanForCar = arg0 -> {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().contains(CAR_BT_DEVICE_NAME)) {
                    selectedDevice = device;
                    binding.tvDeviceInfo.setText("Selected Device: " + device.getName() + " (" + device.getAddress() + ")");
                }
            }
        }

        if (selectedDevice == null) {
            Log.d(TAG, "mSelectOrScanForCar: starting discovery");
            mBluetoothAdapter.cancelDiscovery();
            mBluetoothAdapter.startDiscovery();
        }
    };

    private final Button.OnClickListener mConnect = arg0 -> {
        Log.d(TAG, "mConnect: starting discovery");
        mConnectThread = new ConnectThread(selectedDevice);
        mConnectThread.start();
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
//                device.getUuids();
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("8b483661-b95a-41f4-acd4-3c9b97d7718d"));
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
            mConnectedThread.write("TEST FROM PHONE TO CAR".getBytes());
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
                            break;
                        case "PONG":
                            Snackbar.make(binding.getRoot(), "Received Ping Reply",
                                    Snackbar.LENGTH_SHORT).show();
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
}