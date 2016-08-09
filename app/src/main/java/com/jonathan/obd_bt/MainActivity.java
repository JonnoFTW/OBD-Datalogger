package com.jonathan.obd_bt;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    TextView mTextView;
    OBDBluetooth device;
    private static int REQUEST_ENABLE_BT = 1;
    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mBluetoothKnown;
    ScrollView mScroll;
    Menu mMenu;
    String mPlotKey = "";
    boolean isPlotting = false;
    HashMap<String, List<Integer>> mODBData = new HashMap<>();
    LineChart mChart;
    private static int POINTS_TO_SHOW = 120;
    TextView mGPSStatus, mOBDStatus, mSDStatus;
    private int mBaudrate = 115200; // set the default baud rate to 115200
    private String mPassword = "AT+PASSWOR=DFRobot\r\n";
    public static final UUID SerialPortUUID = UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    public static final UUID CommandUUID = UUID.fromString("0000dfb2-0000-1000-8000-00805f9b34fb");
    public static final UUID ModelNumberStringUUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    private String mBaudrateBuffer = "AT+CURRUART=" + mBaudrate + "\r\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mTextView = (TextView) findViewById(R.id.tv_log);
        mScroll = (ScrollView) findViewById(R.id.scroll);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mChart = (LineChart) findViewById(R.id.chart);
        mChart.setData(mLineData);
        mChart.setTouchEnabled(false);
        mOBDStatus = (TextView) findViewById(R.id.obd_status);
        mGPSStatus = (TextView) findViewById(R.id.gps_status);
        mSDStatus = (TextView) findViewById(R.id.sd_status);
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            mTextView.setText("Device does not support bluetooth");
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
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
        return true;
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_stop) {
            // stop receiving bluetooth
            if (device != null) {
                device.cancel();
                device = null;
            }
        } else if (id == R.id.action_export) {
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
                emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "readings from the logger");

                // the mail subject
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "OBD Logger App output");
                startActivity(Intent.createChooser(emailIntent, "Send email..."));
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, e.getMessage());
            }

            return true;
        } else if (id == 0) {
            // turn plotting off and show the text log
            isPlotting = false;
            mScroll.setVisibility(View.VISIBLE);
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
            mScroll.setVisibility(View.GONE);
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

        } else if (id == R.id.action_connect) {
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
            // If there are paired devices
            if (mBluetoothKnown.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : mBluetoothKnown) {
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
                            device.gatConnect();
                            dialog.dismiss();
                        }
                    });
            builder.show();

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

    protected void updateLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (mTextView) {
                    mTextView.append(msg);
                    mTextView.append("\n");
                    mScroll.fullScroll(ScrollView.FOCUS_DOWN);
                }
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

    private static SparseArray<String> pidMap = new SparseArray<String>(256) {
        {

            put(0xA, "PID_GPS_LATITUDE");
            put(0xB, "PID_GPS_LONGITUDE");
            put(0xC, "PID_GPS_ALTITUDE");
            put(0xD, "PID_GPS_SPEED");
            put(0xE, "PID_GPS_HEADING");
            put(0xF, "PID_GPS_SAT_COUNT");
            put(0x10, "PID_GPS_TIME");
            put(0x11, "PID_GPS_DATE");

            put(0x20, "PID_ACC");
            put(0x21, "PID_GYRO");
            put(0x22, "PID_COMPASS");
            put(0x23, "PID_MEMS_TEMP");
            put(0x24, "PID_BATTERY_VOLTAGE");

            put(0x80, "PID_DATA_SIZE");

            put(0x104, "PID_ENGINE_LOAD");
            put(0x105, "PID_COOLANT_TEMP");
            put(0x106, "PID_SHORT_TERM_FUEL_TRIM_1");
            put(0x107, "PID_LONG_TERM_FUEL_TRIM_1");
            put(0x108, "PID_SHORT_TERM_FUEL_TRIM_2");
            put(0x109, "PID_LONG_TERM_FUEL_TRIM_2");
            put(0x10A, "PID_FUEL_PRESSURE");
            put(0x10B, "PID_INTAKE_MAP");
            put(0x10C, "PID_RPM");
            put(0x10D, "PID_SPEED");
            put(0x10E, "PID_TIMING_ADVANCE");
            put(0x10F, "PID_INTAKE_TEMP");
            put(0x110, "PID_MAF_FLOW");
            put(0x111, "PID_THROTTLE");
            put(0x11E, "PID_AUX_INPUT");
            put(0x11F, "PID_RUNTIME");
            put(0x121, "PID_DISTANCE_WITH_MIL");
            put(0x12C, "PID_COMMANDED_EGR");
            put(0x12D, "PID_EGR_ERROR");
            put(0x12E, "PID_COMMANDED_EVAPORATIVE_PURGE");
            put(0x12F, "PID_FUEL_LEVEL");
            put(0x130, "PID_WARMS_UPS");
            put(0x131, "PID_DISTANCE");
            put(0x132, "PID_EVAP_SYS_VAPOR_PRESSURE");
            put(0x133, "PID_BAROMETRIC");
            put(0x13C, "PID_CATALYST_TEMP_B1S1");
            put(0x13D, "PID_CATALYST_TEMP_B2S1");
            put(0x13E, "PID_CATALYST_TEMP_B1S2");
            put(0x13F, "PID_CATALYST_TEMP_B2S2");
            put(0x142, "PID_CONTROL_MODULE_VOLTAGE");
            put(0x143, "PID_ABSOLUTE_ENGINE_LOAD");
            put(0x145, "PID_RELATIVE_THROTTLE_POS");
            put(0x146, "PID_AMBIENT_TEMP");
            put(0x147, "PID_ABSOLUTE_THROTTLE_POS_B");
            put(0x148, "PID_ABSOLUTE_THROTTLE_POS_C");
            put(0x149, "PID_ACC_PEDAL_POS_D");
            put(0x14A, "PID_ACC_PEDAL_POS_E");
            put(0x14B, "PID_ACC_PEDAL_POS_F");
            put(0x14C, "PID_COMMANDED_THROTTLE_ACTUATOR");
            put(0x14D, "PID_TIME_WITH_MIL");
            put(0x14E, "PID_TIME_SINCE_CODES_CLEARED");
            put(0x152, "PID_ETHANOL_FUEL");
            put(0x159, "PID_FUEL_RAIL_PRESSURE");
            put(0x15B, "PID_HYBRID_BATTERY_PERCENTAGE");
            put(0x15C, "PID_ENGINE_OIL_TEMP");
            put(0x15D, "PID_FUEL_INJECTION_TIMING");
            put(0x15E, "PID_ENGINE_FUEL_RATE");
            put(0x161, "PID_ENGINE_TORQUE_DEMANDED");
            put(0x162, "PID_ENGINE_TORQUE_PERCENTAGE");
            put(0x163, "PID_ENGINE_REF_TORQUE");
        }
    };

    private static String getPIDName(int pid) {
        String out = pidMap.get(pid);
        if (out != null)
            return pidMap.get(pid).substring(4);
        else
            return Integer.toString(pid);

    }

    class OBDBluetooth {
        private ConnectThreadGatt mConnectThread;
        private static final String TAG = "OBDBluetooth";
        public UUID MY_UUID;

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

        public void gatConnect() {
            mConnectThread = new ConnectThreadGatt(mBluetoothDevice);
            mConnectThread.start();
        }

        public void cancel() {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        private class ConnectThreadGatt extends Thread {
            private BluetoothGatt mBluetoothGatt;
            private final BluetoothDevice mBluetoothDevice;
            private String lineBuffer = null;
            private BluetoothGattCharacteristic mSCharacteristic,
                    mModelNumberCharacteristic,
                    mSerialPortCharacteristic,
                    mCommandCharacteristic;
            private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {
                private boolean mConnected = false;

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    SparseArray<String> statuses = new SparseArray<>();
                    statuses.append(BluetoothProfile.STATE_CONNECTED, "CONNECTED");
                    statuses.append(BluetoothProfile.STATE_CONNECTING, "CONNECTING");
                    statuses.append(BluetoothProfile.STATE_DISCONNECTED, "DISCONNECTED");
                    statuses.append(BluetoothProfile.STATE_DISCONNECTING, "DISCONNECTING");
                    updateLog("BLE Connection state changed: " + statuses.get(newState, "unknown"));
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (mBluetoothGatt.discoverServices()) {
                            Log.i(TAG, "Attempting to start service discovery:");

                        } else {
                            Log.e(TAG, "Attempting to start service discovery:not success");

                        }
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        updateLog("Discovered Services Successfully");
                        UUID uuid = null;
                        mModelNumberCharacteristic = null;
                        mSerialPortCharacteristic = null;
                        mCommandCharacteristic = null;

                        // Loops through available GATT Services.
                        for (BluetoothGattService gattService : gatt.getServices()) {
                            uuid = gattService.getUuid();
//                        System.out.println("displayGattServices + uuid="+uuid.toString());

                            List<BluetoothGattCharacteristic> gattCharacteristics =
                                    gattService.getCharacteristics();

                            // Loops through available Characteristics.
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                                uuid = gattCharacteristic.getUuid();
                                if (uuid.equals(ModelNumberStringUUID)) {
                                    mModelNumberCharacteristic = gattCharacteristic;
                                    Log.i(TAG, "mModelNumberCharacteristic  " + mModelNumberCharacteristic.getUuid().toString());
                                } else if (uuid.equals(SerialPortUUID)) {
                                    mSerialPortCharacteristic = gattCharacteristic;
                                    Log.i(TAG, "mSerialPortCharacteristic  " + mSerialPortCharacteristic.getUuid().toString());
                                } else if (uuid.equals(CommandUUID)) {
                                    mCommandCharacteristic = gattCharacteristic;
                                    Log.i(TAG, "mSerialPortCharacteristic  " + mSerialPortCharacteristic.getUuid().toString());
                                }
                            }
                        }

                        mSCharacteristic = mModelNumberCharacteristic;
                        gatt.setCharacteristicNotification(mSCharacteristic, true);
                        Log.i(TAG, "Reading mModelNumber");
                        gatt.readCharacteristic(mSCharacteristic);

                    } else {
                        updateLog("Could not find GATT services");
                    }

                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        String s = new String(data);
//                        Log.i(TAG, "Characteristic Changed to "+ s);

                        if (!characteristic.getUuid().equals(SerialPortUUID) && mConnected) {
                            gatt.setCharacteristicNotification(characteristic, false);
                        } else {
                            if (lineBuffer != null) {
                                s = lineBuffer + s;
                                lineBuffer = null;
                            }
                            Log.i(TAG, "received:" + s);
                            String[] lines = s.trim().split("\\r\\n|\\n|\\r");
                            for (String line : lines) {
                                if (line.matches("(OBD|GPS|MEMS|SD) (.*)")) {
                                    receivedLine("0,"+line.replace(' ',','));
                                } else {
                                    String[] pieces = line.split(",");
                                    if (pieces.length != 3) {
                                        lineBuffer = line;
                                        continue;
                                    }
                                    receivedLine(line);
                                }
                            }
                        }
                    }

                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        String s = new String(characteristic.getValue());
                        updateLog("onCharacteristicRead  " + characteristic.getUuid().toString() + s);
                        if (s.toUpperCase().startsWith("DF BLUNO")) {
                            Log.i(TAG, "Setting password and BAUD");
                            gatt.setCharacteristicNotification(mModelNumberCharacteristic, false);
                            characteristic.setValue(mPassword);
                            gatt.writeCharacteristic(characteristic);
                            characteristic.setValue(mBaudrateBuffer);
                            gatt.writeCharacteristic(characteristic);
                            gatt.setCharacteristicNotification(mSerialPortCharacteristic, true);
                            mConnected = true;
                            updateLog("Receiving");
                        }
                    }
                }
            };

            private void receivedLine(final String message) {
                final String[] pieces = message.split(",");
//                Log.i(TAG, String.format("Parsing (len: %d) %s #", message.length(), message));
                if (pieces[1].matches("(OBD|GPS|SD)")) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            HashMap<String, TextView> statusViews = new HashMap<String, TextView>(4);
                            statusViews.put("OBD", mOBDStatus);
                            statusViews.put("GPS", mGPSStatus);
                            statusViews.put("SD", mSDStatus);
                            try {
                                statusViews.get(pieces[1]).setText(String.format("%s: %s", pieces[1], pieces[2]));
                            } catch (Exception e) {
                                // lol
                            }
                        }
                    });
                } else {
                    if (pieces.length >= 3) {
                        // we have valuable data, store it!
                        String key = pieces[1];
                        // try to translate the key
                        try {
                            int pid = Integer.parseInt(key, 16);
                            String newKey = getPIDName(pid);
                            pieces[1] = newKey != null ? newKey : key;
                            key = pieces[1];
                        } catch (NumberFormatException e) {
                            // arduino already translated
                            // probably looking at mems or gps data
                        }
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
                        final int data = Integer.parseInt(pieces[2]);

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
                    }
                    if (!isPlotting)
                        updateLog(String.format("%s\t%s\t%s", pieces[0], pieces[1], pieces[2]));
                }
            }


            public ConnectThreadGatt(BluetoothDevice device) {
                mBluetoothDevice = device;
            }

            public void cancel() {
                if (mBluetoothGatt != null) {
                    updateLog("Cancelling BLE connection");
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                }
            }

            public void run() {
                synchronized (this) {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, btleGattCallback);
                }

            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first

        // if the bluetooth is running, pause it
        if (device != null) {
            device.cancel();
        }
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        // if the bluetooth is running, pause it
        if (device != null) {
            device.gatConnect();
        }
    }
}
