package com.jonathan.obd_bt;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

public class MainActivity extends AppCompatActivity {
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

    private void setTopMenu() {
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
        mPIDMenu.setHeaderTitle("PID");
        setTopMenu();
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
        } else if (id == 0) {
            // turn plotting off and show the text log
            isPlotting = false;
            mScroll.setVisibility(View.VISIBLE);
            mChart.setVisibility(View.GONE);
            updateLog("Closing plot");
            isPlotting = false;
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
                            device.connect();
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


            data.addXValue(data.getXValCount()+"");
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
                mTextView.append(msg);
                mTextView.append("\n");
                mScroll.fullScroll(ScrollView.FOCUS_DOWN);
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
        }

        public synchronized void connected(BluetoothSocket socket,
                                           BluetoothDevice device) {
            Log.i(TAG, "connected, Socket to " + device.getName());
            updateLog("Connection successful");
            // Start the thread to manage the connection and perform
            // transmissions

            mConnectedThread = new ConnectedThread(socket);
            mConnectedThread.start();

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
                    updateLog("error creating insecure socket" + e.getMessage());
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
                    updateLog(connectException.getMessage());
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
                updateLog("Cancelling connection");
                try {
                    mmSocket.close();
                } catch (IOException e) {
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
            device.connect();
        }
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
        private  String getPIDName(int pid) {
            SparseArray<String> arr = new SparseArray<>(256);
            arr.put(0x104,"PID_ENGINE_LOAD");
            arr.put(0x105,"PID_COOLANT_TEMP");
            arr.put(0x106,"PID_SHORT_TERM_FUEL_TRIM_1");
            arr.put(0x107,"PID_LONG_TERM_FUEL_TRIM_1");
            arr.put(0x108,"PID_SHORT_TERM_FUEL_TRIM_2");
            arr.put(0x109,"PID_LONG_TERM_FUEL_TRIM_2");
            arr.put(0x10A,"PID_FUEL_PRESSURE");
            arr.put(0x10B,"PID_INTAKE_MAP");
            arr.put(0x10C,"PID_RPM");
            arr.put(0x10D,"PID_SPEED");
            arr.put(0x10E,"PID_TIMING_ADVANCE");
            arr.put(0x10F,"PID_INTAKE_TEMP");
            arr.put(0x110,"PID_MAF_FLOW");
            arr.put(0x111,"PID_THROTTLE");
            arr.put(0x11E,"PID_AUX_INPUT");
            arr.put(0x11F,"PID_RUNTIME");
            arr.put(0x121,"PID_DISTANCE_WITH_MIL");
            arr.put(0x12C,"PID_COMMANDED_EGR");
            arr.put(0x12D,"PID_EGR_ERROR");
            arr.put(0x12E,"PID_COMMANDED_EVAPORATIVE_PURGE");
            arr.put(0x12F,"PID_FUEL_LEVEL");
            arr.put(0x130,"PID_WARMS_UPS");
            arr.put(0x131,"PID_DISTANCE");
            arr.put(0x132,"PID_EVAP_SYS_VAPOR_PRESSURE");
            arr.put(0x133,"PID_BAROMETRIC");
            arr.put(0x13C,"PID_CATALYST_TEMP_B1S1");
            arr.put(0x13D,"PID_CATALYST_TEMP_B2S1");
            arr.put(0x13E,"PID_CATALYST_TEMP_B1S2");
            arr.put(0x13F,"PID_CATALYST_TEMP_B2S2");
            arr.put(0x142,"PID_CONTROL_MODULE_VOLTAGE");
            arr.put(0x143,"PID_ABSOLUTE_ENGINE_LOAD");
            arr.put(0x145,"PID_RELATIVE_THROTTLE_POS");
            arr.put(0x146,"PID_AMBIENT_TEMP");
            arr.put(0x147,"PID_ABSOLUTE_THROTTLE_POS_B");
            arr.put(0x148,"PID_ABSOLUTE_THROTTLE_POS_C");
            arr.put(0x149,"PID_ACC_PEDAL_POS_D");
            arr.put(0x14A,"PID_ACC_PEDAL_POS_E");
            arr.put(0x14B,"PID_ACC_PEDAL_POS_F");
            arr.put(0x14C,"PID_COMMANDED_THROTTLE_ACTUATOR");
            arr.put(0x14D,"PID_TIME_WITH_MIL");
            arr.put(0x14E,"PID_TIME_SINCE_CODES_CLEARED");
            arr.put(0x152,"PID_ETHANOL_FUEL");
            arr.put(0x159,"PID_FUEL_RAIL_PRESSURE");
            arr.put(0x15B,"PID_HYBRID_BATTERY_PERCENTAGE");
            arr.put(0x15C,"PID_ENGINE_OIL_TEMP");
            arr.put(0x15D,"PID_FUEL_INJECTION_TIMING");
            arr.put(0x15E,"PID_ENGINE_FUEL_RATE");
            arr.put(0x161,"PID_ENGINE_TORQUE_DEMANDED");
            arr.put(0x162,"PID_ENGINE_TORQUE_PERCENTAGE");
            arr.put(0x163,"PID_ENGINE_REF_TORQUE");
            String out = arr.get(pid);
            if(out != null)
                return arr.get(pid).substring(4);
            else
                return Integer.toString(pid);

        }
        public void run() {
            int line = 0;
            // Keep listening to the InputStream until an exception occurs
            BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
            while (true) {
                try {
                    String message = reader.readLine();
                    if (message.matches(".*\\p{Cntrl}.*")) {
                        Log.i(TAG, "Received junk: " + message);
                    } else {
                        if(message.startsWith("Translating")) {
                            try {
                                String[] pieces = message.split(" ");

                                int pid = Integer.parseInt(pieces[pieces.length - 1]);
                                message += String.format(" %s", getPIDName(pid));
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                        final String[] pieces = message.split(",");
                        if (pieces.length >= 3) {
                            // we have valuable data, store it!
                            String key = pieces[1];
                            // try to translate the key
                            try {
                                int pid = Integer.parseInt(key);
                                String newKey = getPIDName(pid);
                                pieces[1] = newKey!=null?newKey:key;
                                key = pieces[1];
                            } catch(NumberFormatException e) {
                                // arduino already translated
                                // probably looking at mems or gps data
                            }
                            if (!mODBData.containsKey(key)) {
                                mODBData.put(key, new ArrayList<Integer>());

                                String[] keys = mODBData.keySet().toArray(new String[mODBData.size()]);
                                Arrays.sort(keys);
                                mPIDMenu.clear();
                                setTopMenu();
                                for (String s : keys) {
                                    mPIDMenu.add(0, hash(s), Menu.NONE, s);
                                    mKeyIds.put(hash(s), s);
                                }
                            }
                            final int data = Integer.parseInt(pieces[2]);
                            StringBuffer stringBuffer = new StringBuffer();

                          /*  for(int i = 2; i < pieces.length; i++) {
                                stringBuffer.append(pieces[i]);
                                if(i+1 < pieces.length)
                                    stringBuffer.append(",");
                            }*/
                            final List<Integer> dataList = Collections.synchronizedList(mODBData.get(key));
                            if(!isListing) {
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
                            updateLog(String.format("[%d] %s", line, message));
                        line++;
                    }
                } catch (IOException e) {
                    updateLog("error reading: " + e.getMessage());
                    break;
                }
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                updateLog("error when closing: " + e.getMessage());
            }
        }
    }
}
