package com.capstone.pkes;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.capstone.pkes.databinding.FragmentSecondBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class CarMode extends Fragment {

    private static final String TAG = "PKES-CarMode";

    private FragmentSecondBinding binding;

    final int MESSAGE_READ = 0;
    final int MESSAGE_WRITE = 1;
    final int MESSAGE_TOAST = 2;
    final int MESSAGE_STATE_CHANGE = 3;

    private ConnectedThread mConnectedThread;
    Location currentLocation = null;
    Timer getLocationTimer = null;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSecond.setOnClickListener(view1 -> NavHostFragment.findNavController(CarMode.this)
                .navigate(R.id.action_SecondFragment_to_FirstFragment));

        binding.btnAcceptConn.setOnClickListener(view12 -> {
            AcceptThread acceptThread = new AcceptThread();
            acceptThread.start();
            binding.tvServerStatus.setText("Server started");
        });

        binding.btnMakeDiscoverable.setOnClickListener(view13 -> {
            Intent discoverableIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, 1);
        });

        // Get Location from main activity
        getLocationTimer = new Timer();
        getLocationTimer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                getLocation();
            }
        },0,1000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (getLocationTimer != null) {
            getLocationTimer.cancel();
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("pkes", UUID.fromString(Constants.BT_SERVICE_UUID));
            } catch (IOException e) {
                Log.e("AcceptThread", "Socket's listen() method failed", e);
                updateServerStatus("Server not running");
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                    if (socket != null) {
                        // A connection was accepted. Perform work associated with
                        // the connection in a separate thread.
//                    manageMyConnectedSocket(socket);
                        updateServerStatus("Connected client, stopping server");
                        mConnectedThread = new ConnectedThread(socket, mHandler);
                        mConnectedThread.start();
                        try {
                            mmServerSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                } catch (IOException e) {
                    Log.e("AcceptThread", "Socket's accept() method failed", e);
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @SuppressLint("HandlerLeak")
        @Override
        public void handleMessage(Message msg) {
//            FragmentActivity activity = getActivity();
            Log.d(TAG, "in handleMessage:");
            Log.d(TAG, msg.toString());
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ConnectedThread.STATE_CONNECTED:
                            Log.d(TAG, "handleMessage: Connected!");
                            break;
                        case ConnectedThread.STATE_CONNECTING:
                            Log.d(TAG, "handleMessage: Connecting...");
                            break;
                        case ConnectedThread.STATE_LISTEN:
                        case ConnectedThread.STATE_NONE:
                            Log.d(TAG, "handleMessage: Not connected (anymore?)!");
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
                        case "LOCK":
                            updateLockStatus(true);
                            return;
                    }

                    String[] actionPayload = data_encryption.decrypt(readMessage).split("\\|");
                    int action = Integer.parseInt(actionPayload[1]);
                    long timestamp = Long.parseLong(actionPayload[2]);
                    long timestampNow = System.currentTimeMillis();

                    switch (action) {
                        case Constants.ACTION_LOCATION_REQUEST:
                            Log.d(TAG, "action: Location Request: " + actionPayload);

                            if ((timestampNow - timestamp) > 1*60*1e3) break;

                            // TODO: Use GPS coordinates in response
                            String locationResponsePayload = "A|" + Constants.ACTION_LOCATION_RESPONSE + "|" + timestampNow + "|" + currentLocation.getLatitude() + "|" + currentLocation.getLongitude();
                            mConnectedThread.write(data_encryption.encrypt(locationResponsePayload).getBytes());
                            break;
                        case Constants.ACTION_UNLOCK_REQUEST:
                            Log.d(TAG, "action: Unlock Request: " + actionPayload);
                            if ((timestampNow - timestamp) > 1*60*1e3) break;

                            updateLockStatus(false);
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

    private void updateServerStatus(String text) {
        new Handler(Looper.getMainLooper()).post(() -> binding.tvServerStatus.setText(text));
    }

    @SuppressLint("SetTextI18n")
    private void updateLockStatus(Boolean locked) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (locked) {
                binding.tvLockText.setText("Locked");
                binding.tvLockText.setTextColor(Color.GREEN);
            } else {
                binding.tvLockText.setText("Unlocked");
                binding.tvLockText.setTextColor(Color.RED);
            }
        });
    }

    private Location getLocation() {
        currentLocation = ((MainActivity) getActivity()).getLocation();
        return currentLocation;
    }
}