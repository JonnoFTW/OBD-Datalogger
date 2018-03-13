package com.jonathan.obd_bt;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dmallcott.progressfloatingactionbutton.ProgressFloatingActionButton;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;


import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String DEVICE_KEYS = "DEVICE_KEYS";
    public static final String BLUETOOTH_KEYS = "BLUETOOTH_KEYS";
    public static final String SERVER_URL = "server_url";
    TextView mTextView;
    OBDBluetooth device;
    private static int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mBluetoothKnown;
    ScrollView mScroll, mTopScroll;
    ProgressFloatingActionButton mDownloadProg;
    Menu mMenu;
    String mPlotKey = "";
    RelativeLayout mContainer;
    boolean isPlotting = false;
    boolean mConnected = false;
    HashMap<String, List<Integer>> mODBData = new HashMap<>();
    HashMap<String, TextView> mOBDLatest = new HashMap<>();
    LineChart mChart;
    private static int POINTS_TO_SHOW = 120;
    TextView mGPSStatus, mOBDStatus, mSDStatus;

    private static final int MY_PERMISSIONS_REQUEST_INTERNET = 10044,
            MY_PERMISSIONS_REQUSET_NETWORK_STATE = 10045;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mTextView = findViewById(R.id.tv_log);
        mScroll = findViewById(R.id.scroll);
        mContainer = findViewById(R.id.notchart);
        mDownloadProg = findViewById(R.id.downloadProgress);
        mTopScroll = findViewById(R.id.top_scroll);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mChart = findViewById(R.id.chart);
        mChart.setData(mLineData);

        mChart.setTouchEnabled(false);
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            mTextView.setText("Device does not support bluetooth");
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
//        setDisconnected();
    }


    final private int REQUEST_CODE_ASK_PERMISSIONS = 420110;

    private void checkPermissions() {

        checkForPermission(new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
        }, REQUEST_CODE_ASK_PERMISSIONS);

    }

    private boolean checkForPermission(final String[] permissions, final int permRequestCode) {
        final List<String> permissionsNeeded = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            final String perm = permissions[i];
            if (ContextCompat.checkSelfPermission(mContext, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(permissions[i])) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(getResources().getString(R.string.permission_title));
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // add the request.
                            permissionsNeeded.add(perm);
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                } else {
                    // add the request.
                    permissionsNeeded.add(perm);
                }
            }
        }

        if (permissionsNeeded.size() > 0) {
            // go ahead and request permissions
            requestPermissions(permissionsNeeded.toArray(new String[permissionsNeeded.size()]), permRequestCode);
            return false;
        } else {
            // no permission need to be asked so all good...we have them all.
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_INTERNET: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // we can do the internet

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void resetMenu() {
        mPIDMenu.clear();
        mPIDMenu.add(0, 0, Menu.NONE, "Readings");
    }

    SubMenu mPIDMenu;
    MenuItem mPIDItem;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mMenu = menu;
        mPIDItem = menu.findItem(R.id.numberSpinner);
        mPIDMenu = mPIDItem.getSubMenu();
        resetMenu();
        mPIDMenu.setHeaderTitle("PID");
        menu.findItem(R.id.action_export).setEnabled(false).getIcon().setAlpha(130);
        return true;
    }

    /**
     * Indicate that we are actually connected to a device
     */
    private void setConnected() {
        mConnected = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.placeholder).setVisibility(View.GONE);
                enableButton(R.id.action_export);
                mMenu.findItem(R.id.action_connect).setIcon(R.drawable.ic_bluetooth_disabled_black_24dp);
            }
        });

    }

    private void setMenuItemsDisabled() {
        findViewById(R.id.placeholder).setVisibility(View.GONE);
        disableButton(R.id.action_export);
        mMenu.findItem(R.id.action_connect).setIcon(R.drawable.ic_bluetooth_black_24dp);
    }

    /**
     * Set the buttons in the menu appropriately for when nothing is connected
     */
    private void setDisconnected() {
        mConnected = false;
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                findViewById(R.id.placeholder).setVisibility(View.VISIBLE);
                disableButton(R.id.action_export);
                mMenu.findItem(R.id.action_connect).setIcon(R.drawable.ic_bluetooth_disabled_black_24dp);
            }
        });


    }

    private int hash(String s) {
        int id = 1;
        for (int i = 0; i < s.length(); i++) {
            id *= s.charAt(i);
        }
        return id;
    }

    private Context mContext = this;
    private HashMap<Integer, String> mKeyIds = new HashMap<>();
    private LineData mLineData = new LineData();
    private boolean isListing = false;

    private void exportEmail() {
        try {
            File file = new File(getExternalFilesDir(null), "obd_log_file" + (new Date()).getTime() + ".csv");
            Log.i(TAG, "File saving to: " + file.getAbsoluteFile());
            OutputStream out = new FileOutputStream(file);
            out.write("time(diff),pid,value\n".getBytes());
            for (String line : mTextView.getText().toString().split("\n")) {
                String[] pieces = line.split(",");
                if (pieces.length >= 3) {
                    pieces[1] = getPIDName(Integer.parseInt(pieces[1], 16));
                    out.write((StringUtils.join(pieces, ",") + "\n").getBytes());
                }
            }
            out.close();
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("text/plain");
            // the attachment

            emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"jonmac1@gmail.com"});
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "readings from the logger");

            // the mail subject
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "OBD Logger App output");
            startActivity(Intent.createChooser(emailIntent, "Send email..."));
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(TAG, e.getMessage());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_export) {
            // send command to begin download files
            if (device != null) {
                updateLog("Fetching completed log files");
                disableButton(R.id.action_export);
                device.send("$export");
            }
            return true;
        } else if (id == 0) {
            // turn plotting off and show the text log
            isPlotting = false;
            mContainer.setVisibility(View.VISIBLE);
            mChart.setVisibility(View.GONE);
            updateLog("Closing plot");
            isPlotting = false;
            return true;
        } else if (mKeyIds.containsKey(id)) {
            // turn plotting on
            mPlotKey = mKeyIds.get(id);
            isPlotting = true;
            updateLog("Opening " + mPlotKey + " plot");
            mChart.setVisibility(View.VISIBLE);
            mContainer.setVisibility(View.GONE);
            // load the data in from the arraylist containing
            // they keys data
            final List<Integer> data = Collections.synchronizedList(mODBData.get(mKeyIds.get(id)));

            mChart.clear();
            mLineData = new LineData();
            mChart.setData(mLineData);
            mChart.getAxisLeft().resetAxisMaxValue();
            mChart.getAxisLeft().resetAxisMinValue();
            isListing = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mChart.getAxisLeft().resetAxisMaxValue();
                    mChart.getAxisLeft().resetAxisMinValue();
                    for (Integer i : data.subList(Math.max(data.size() - POINTS_TO_SHOW, 0), data.size())) {
                        addEntry(i);
                    }
                    mChart.invalidate();
                }
            });

            isListing = false;

        } else if (id == R.id.action_preferences) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);

        } else if (id == R.id.action_send_msg && mConnected) {

            final String[] messages = "$ip,$serial,$list_log,$err,$systime,$reset,$resetwifi".split(",");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setSingleChoiceItems(messages, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(which >= 0) {
                        String strName = messages[which];
                        System.out.println("Clicked: " + strName);
                    }
                }
            });
            builder.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ListView lw = ((AlertDialog)dialog).getListView();
                    int selectedPos = lw.getCheckedItemPosition();
                    if(selectedPos >= 0) {
                        String strName = messages[selectedPos];
                        System.out.println("Sending: " + strName);
                        device.send(strName);
                    }

                    dialog.dismiss();
                }
            });


            builder.setTitle("Send Message");
            builder.create();
            builder.show();
        } else if (id == R.id.action_connect && !mConnected) {
            mTextView.setText("");
            if (device != null) {
                device.cancel();
                device = null;
                mLineData.clearValues();
                mODBData.clear();
                resetMenu();
            }
            final ArrayList<CharSequence> items = new ArrayList<>(8);
            mBluetoothKnown = mBluetoothAdapter.getBondedDevices();
            final String uuid = "08be0e96-6ab4-11e7-907b-a6006ad3dba0";
            // If there are paired devices

            if (mBluetoothKnown.size() > 0) {
                // Loop through paired devices
                // only add those with our the rpi-logger UUID
                for (BluetoothDevice device : mBluetoothKnown) {
                    if(device.getName().startsWith("rpi-logger"))
                    // Add the name and address to an array adapter to show in a ListView
                        items.add(device.getName() + "\n" + device.getAddress());
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a Device")
                    .setItems(items.toArray(new CharSequence[items.size()]), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            device = new OBDBluetooth(items.get(which).toString().split("\n")[1]);
                            device.connect();
                            dialog.dismiss();
                        }
                    });
            builder.show();

            return true;

        } else if (id == R.id.action_connect && mConnected) {
            // stop receiving bluetooth
            if (device != null) {
                device.cancel();
                device = null;
            }
        } else if (id == R.id.action_upload) {
            uploadData();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, mPlotKey + " Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(1.5f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private void addEntry(int value) {

        LineData data = mChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }


            data.addXValue(data.getXValCount() + "");
            data.addEntry(new Entry((float) value, set.getEntryCount()), 0);


            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(POINTS_TO_SHOW);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getXValCount() - POINTS_TO_SHOW + 1);

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private void updateLogErr(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String html = "<font color=\"#cc1111\">" + message + "</font>";
                if (Build.VERSION.SDK_INT >= 24) {
                    mTextView.append(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)); // for 24 api and more
                } else {
                    mTextView.append(Html.fromHtml(html)); // or for older api
                }
                mTextView.append("\n");
                mScroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    protected void updateLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.append(msg);
                mTextView.append("\n");
                mScroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void updateOBD(final TextView tv, final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv.setText(msg);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    private static String getPIDName(int pid) {
        SparseArray<String> arr = new SparseArray<>(256);
        arr.put(0x104, "PID_ENGINE_LOAD");
        arr.put(0x105, "PID_COOLANT_TEMP");
        arr.put(0x106, "PID_SHORT_TERM_FUEL_TRIM_1");
        arr.put(0x107, "PID_LONG_TERM_FUEL_TRIM_1");
        arr.put(0x108, "PID_SHORT_TERM_FUEL_TRIM_2");
        arr.put(0x109, "PID_LONG_TERM_FUEL_TRIM_2");
        arr.put(0x10A, "PID_FUEL_PRESSURE");
        arr.put(0x10B, "PID_INTAKE_MAP");
        arr.put(0x10C, "PID_RPM");
        arr.put(0x10D, "PID_SPEED");
        arr.put(0x10E, "PID_TIMING_ADVANCE");
        arr.put(0x10F, "PID_INTAKE_TEMP");
        arr.put(0x110, "PID_MAF_FLOW");
        arr.put(0x111, "PID_THROTTLE");
        arr.put(0x11E, "PID_AUX_INPUT");
        arr.put(0x11F, "PID_RUNTIME");
        arr.put(0x121, "PID_DISTANCE_WITH_MIL");
        arr.put(0x12C, "PID_COMMANDED_EGR");
        arr.put(0x12D, "PID_EGR_ERROR");
        arr.put(0x12E, "PID_COMMANDED_EVAPORATIVE_PURGE");
        arr.put(0x12F, "PID_FUEL_LEVEL");
        arr.put(0x130, "PID_WARMS_UPS");
        arr.put(0x131, "PID_DISTANCE");
        arr.put(0x132, "PID_EVAP_SYS_VAPOR_PRESSURE");
        arr.put(0x133, "PID_BAROMETRIC");
        arr.put(0x13C, "PID_CATALYST_TEMP_B1S1");
        arr.put(0x13D, "PID_CATALYST_TEMP_B2S1");
        arr.put(0x13E, "PID_CATALYST_TEMP_B1S2");
        arr.put(0x13F, "PID_CATALYST_TEMP_B2S2");
        arr.put(0x142, "PID_CONTROL_MODULE_VOLTAGE");
        arr.put(0x143, "PID_ABSOLUTE_ENGINE_LOAD");
        arr.put(0x145, "PID_RELATIVE_THROTTLE_POS");
        arr.put(0x146, "PID_AMBIENT_TEMP");
        arr.put(0x147, "PID_ABSOLUTE_THROTTLE_POS_B");
        arr.put(0x148, "PID_ABSOLUTE_THROTTLE_POS_C");
        arr.put(0x149, "PID_ACC_PEDAL_POS_D");
        arr.put(0x14A, "PID_ACC_PEDAL_POS_E");
        arr.put(0x14B, "PID_ACC_PEDAL_POS_F");
        arr.put(0x14C, "PID_COMMANDED_THROTTLE_ACTUATOR");
        arr.put(0x14D, "PID_TIME_WITH_MIL");
        arr.put(0x14E, "PID_TIME_SINCE_CODES_CLEARED");
        arr.put(0x152, "PID_ETHANOL_FUEL");
        arr.put(0x159, "PID_FUEL_RAIL_PRESSURE");
        arr.put(0x15B, "PID_HYBRID_BATTERY_PERCENTAGE");
        arr.put(0x15C, "PID_ENGINE_OIL_TEMP");
        arr.put(0x15D, "PID_FUEL_INJECTION_TIMING");
        arr.put(0x15E, "PID_ENGINE_FUEL_RATE");
        arr.put(0x161, "PID_ENGINE_TORQUE_DEMANDED");
        arr.put(0x162, "PID_ENGINE_TORQUE_PERCENTAGE");
        arr.put(0x163, "PID_ENGINE_REF_TORQUE");
        String out = arr.get(pid);
        if (out != null)
            return arr.get(pid).substring(4);
        else
            return Integer.toString(pid);

    }

    class OBDBluetooth {
        private ConnectedThread mConnectedThread;
        private ConnectThread mConnectThread;
        private static final String TAG = "OBDBluetooth";
        public UUID MY_UUID;

        public static final int STATE_NONE = 0; // we're doing nothing
        public static final int STATE_LISTEN = 1; // now listening for incoming
        // connections
        public static final int STATE_CONNECTING = 2; // now initiating an
        // outgoing connection
        public static final int STATE_CONNECTED = 3; // now connected to a
        // remote device
        public int mState;
        BluetoothDevice mBluetoothDevice;

        public OBDBluetooth(String address) {
            for (BluetoothDevice btd : mBluetoothKnown) {
                if (btd.getAddress().equals(address)) {
                    mBluetoothDevice = btd;
                    break;
                }
            }


            MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            updateLog("Trying to connect to " + address);
        }

        public void send(final String msg) {
            if (mState == STATE_CONNECTED) {
                mConnectedThread.send(msg);
            }
        }

        public void connect() {
            // Cancel any thread attempting to make a connection
            if (mState == STATE_CONNECTING) {
                if (mConnectThread != null) {
                    mConnectThread.cancel();
                    mConnectThread = null;
                }
            }

            // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }

            // Start the thread to connect with the given device
            mConnectThread = new ConnectThread(mBluetoothDevice);
            mConnectThread.start();
        }

        public void cancel() {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }

            setDisconnected();
        }

        public synchronized void connected(BluetoothSocket socket,
                                           BluetoothDevice device) {
            Log.i(TAG, "connected, Socket to " + device.getName());
            updateLog("Connection successful");
            // Start the thread to manage the connection and perform
            // transmissions
            mState = STATE_CONNECTED;
            mConnectedThread = new ConnectedThread(socket);
            mConnectedThread.start();

            setConnected();


        }

        private class ConnectThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final BluetoothDevice mmDevice;

            public ConnectThread(BluetoothDevice device) {
                // Use a temporary object that is later assigned to mmSocket,
                // because mmSocket is final
                BluetoothSocket tmp = null;
                mmDevice = device;
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    // MY_UUID is the app's UUID string, also used by the server code
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                } catch (IOException e) {
                    updateLogErr("error creating insecure socket" + e.getMessage());
                }
                mmSocket = tmp;
            }

            public void run() {
                // Cancel discovery because it will slow down the connection
                mBluetoothAdapter.cancelDiscovery();

                try {
                    // Connect the device through the socket. This will block
                    // until it succeeds or throws an exception
                    mmSocket.connect();
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    updateLogErr(connectException.getMessage());
                    cancel();
                    return;
                }

                // Do work to manage the connection (in a separate thread)
                connected(mmSocket, mmDevice);
            }

            /**
             * Will cancel an in-progress connection, and close the socket
             */
            public void cancel() {
                updateLogErr("Cancelling connection");
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    updateLogErr("Could not cancel connection: " + e.getMessage());
                } finally {
                    setDisconnected();
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first

        // if the bluetooth is running, pause it
//        if (device != null) {
//            device.cancel();
//        }
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        // if the bluetooth is running, pause it
//        if (device != null) {
//            device.connect();
//        }
    }

    /**
     * Send data to the api,
     * include:
     * keys: a list of keys for devices
     * data[]: an array filled with gzipped json data in each field
     */
    public void uploadData() {
        // Launch a new activity and go from there
        Intent i = new Intent(this, ExportActivity.class);
        startActivity(i);
    }

    private class ConnectedThread extends Thread {
        private static final String TAG = "ConnectedThread";
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;


        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {

                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                updateLog("error opening " + e.getMessage());
            }
            mmOutStream = tmpOut;
            mmInStream = tmpIn;

        }

        public void send(final String msg) {
            try {
                mmOutStream.write(msg.getBytes());
            } catch (IOException e) {
                updateLogErr("Could not send message: '" + msg + "' " + e.getMessage());
                // probably can't send because we're disconnected
                setDisconnected();
            }
        }


        public void run() {
            int line = 0;
            // Keep listening to the InputStream until an exception occurs
            // send $login=password
            try {
                updateLog("Logging in");
                SharedPreferences prefs = getSharedPreferences(BLUETOOTH_KEYS, 0);
                String password = prefs.getString(mmSocket.getRemoteDevice().getAddress(), "secret_password_here");

                mmOutStream.write(("$login=" + password).getBytes());
            } catch (IOException e) {
                updateLogErr("Couldn't login");
            }
            boolean exporting = false;
            BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
            while (true) {
                try {
//                    updateLog("\n----------------------");
                    final String message = reader.readLine();
//                    updateLog(message);
                    if (message.startsWith("$")) {
                        updateLog(message);
                        if (message.startsWith("$export")) {
                            String[] parts = message.split("=");
                            exporting = true;
                            // read everything on the reader and convert it to bytes?
                            if (parts[1].equals("done")) {
                                // nothing to send

                            } else {
                                int byte_count = Integer.parseInt(parts[1]);
                                String fname = parts[2];
                                receiveFiles(byte_count, fname, reader);
                            }
                            exporting = false;
                            enableExportButton();
                        }

                        continue;
                    }
                    JSONObject json = new JSONObject(message);
                    // iterate over the json object
                    Iterator<String> iter = json.keys();

                    while (iter.hasNext()) {
                        String key = iter.next();
                        if (!mOBDLatest.containsKey(key)) {
                            final TextView tv = new TextView(mContext);
                            mOBDLatest.put(key, tv);
                            final LinearLayout ll = (LinearLayout) findViewById(R.id.value_texts);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ll.addView(tv);
                                }
                            });

                        }
                        TextView latestTv = mOBDLatest.get(key);
                        if (!mODBData.containsKey(key)) {

                            mODBData.put(key, new ArrayList<Integer>());

                            String[] keys = mODBData.keySet().toArray(new String[mODBData.size()]);
                            Arrays.sort(keys);

                            resetMenu();
                            for (String s : keys) {
                                mPIDMenu.add(0, hash(s), Menu.NONE, s);
                                mKeyIds.put(hash(s), s);
                            }
                        }
                        try {
                            if (json.isNull(key)) {
                                updateOBD(latestTv, key + ": null");
//                                updateLog(key + ": null");
                                continue;
                            }

                            if (key.equals("pos")) {
                                NumberFormat fmt = new DecimalFormat("#0.0000");
                                JSONArray coords = json.getJSONObject("pos").getJSONArray("coordinates");
                                updateOBD(latestTv, String.format("Latitude: %s\nLongitude: %s", fmt.format(coords.getDouble(1)), fmt.format(coords.getDouble(0))));
                                continue;
                            }
                            int fallback = -2555;
                            final double dataDouble = json.optDouble(key, -255);
                            final int data = json.optInt(key, fallback);

                            if (data != fallback) {
                                if (dataDouble != -255) {
                                    updateOBD(latestTv, String.format("%s: %.2f", key, dataDouble));
                                } else {
                                    updateOBD(latestTv, String.format("%s: %d", key, data));
                                }
                                final List<Integer> dataList = mODBData.get(key);
                                if (!isListing) {
                                    dataList.add(data);
                                    // add it to the current line data if we are on the right key
                                    if (mPlotKey.equals(key)) {
                                        if (isPlotting) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addEntry(data);
                                                    mChart.invalidate();
                                                }
                                            });
                                        }
                                    }
                                }
                            } else {
                                if (json.optString(key) != null) {
                                    updateOBD(latestTv, key + ": " + json.getString(key));
                                }
                            }
                        } catch (JSONException e) {
                            updateLogErr(message);
                        }
                    }
                    if (!isPlotting)
//                        updateLog(String.format("[%d] %s", line, message));
                        line++;
                } catch (IOException e1) {
                    updateLogErr("error reading: " + e1.getMessage());
                    e1.printStackTrace();
                    break;
                } catch (JSONException e) {
                    updateLogErr(e.getMessage());
                    e.printStackTrace();
                }
            }
            setDisconnected();
        }

        public boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                return true;
            }
            return false;
        }

        private void receiveFiles(final int byte_count, final String fname, final BufferedReader reader) {
            // files come in as base64 encoded gzip,
            // just save them into another gzip
            try {

                if (byte_count <= 0) {
                    updateLog("Ignoring empty file " + fname);
                    return;
                }
                File outFile = new File(mContext.getFilesDir() + "/" + fname.replace("!", ""));
                outFile.mkdirs();
                if (outFile.exists()) {
                    outFile.delete();
                }
                if (outFile.createNewFile()) {
                    updateLog("Writing to: " + outFile.getName());
                    FileOutputStream fileOutputStream = null;
                    try {
                        fileOutputStream = new FileOutputStream(outFile);
                        int written = 0;
                        resetDownloadFab(byte_count);
                        StringBuffer sb = new StringBuffer(byte_count);
                        while (true) {
                            final String line = reader.readLine();
//                            System.out.println(line);
                            if (line.equals("$done")) {
                                break;
                            }
                            if (line.startsWith("$export=")) {
                                String toWrite = line.substring(8);
                                written += toWrite.getBytes().length;
                                sb.append(toWrite);
                                final int finalWritten = written;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mDownloadProg.setCurrentProgress(finalWritten, true);
                                    }
                                });
                            }
                        }
//                        System.out.println(sb.toString());
                        try {
                            fileOutputStream.write(Base64.decode(sb.toString().getBytes(), Base64.DEFAULT));
                        } catch (IllegalArgumentException e) {
                            updateLog("Invalid file: "+outFile.getName());
                        }

                    } finally {
                        if (fileOutputStream != null)
                            fileOutputStream.close();
                        hideDownloadFab();
                    }

                }
            } catch (IOException e) {
                updateLogErr("Could not write out file: " + e.getMessage());
                e.printStackTrace();
            }

        }

        /* Call this from the main activity to shutdown the connection */

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                updateLogErr("error when closing: " + e.getMessage());
            }
        }
    }


    private void disableButton(final int id) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuItem item = mMenu.findItem(id);
                item.setEnabled(false);
                item.getIcon().setAlpha(130);
            }
        });
    }

    private void enableButton(final int id) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuItem item = mMenu.findItem(id);
                item.setEnabled(true);
                item.getIcon().setAlpha(255);
            }
        });
    }

    private void enableExportButton() {
        enableButton(R.id.action_export);
    }

    private void resetDownloadFab(final int total_bytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                slideUp(mDownloadProg);
                mDownloadProg.setVisibility(View.VISIBLE);
                mDownloadProg.setStartingProgress(0);
                mDownloadProg.setCurrentProgress(0, true);
                mDownloadProg.setTotalProgress(total_bytes);
                mDownloadProg.setStepSize(1);
            }
        });
    }

    public void slideToBottom(View view) {
        TranslateAnimation animate = new TranslateAnimation(0, 0, 0, view.getHeight());
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.GONE);
    }

    public void slideUp(View view) {
        view.setVisibility(View.VISIBLE);
        TranslateAnimation animate = new TranslateAnimation(0, 0, 0, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    private void hideDownloadFab() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                slideToBottom(mDownloadProg);
            }
        });
    }
}
