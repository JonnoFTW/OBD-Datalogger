package com.jonathan.obd_bt;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.jonathan.obd_bt.Adapters.FileListAdapter;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class ExportActivity extends AppCompatActivity {


    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private FileListAdapter mListAdapter;
    private FloatingActionButton fab;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mContext = this;
        fab = (FloatingActionButton) findViewById(R.id.fab_upload);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Uploading files", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                // upload selected files
                ArrayList<File> toUpload = new ArrayList<>();
                for (int i = 0; i < mListAdapter.getItemCount(); i++) {
                    File f = mListAdapter.getItem(i);
                    if (mListAdapter.isSelected(i)) {
                        toUpload.add(f);
                    }
                }
                uploadFile(toUpload);
            }
        });
        mListAdapter = new FileListAdapter(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLayoutManager = new LinearLayoutManager(this);

        mRecyclerView = findViewById(R.id.listview);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mListAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.export_menu, menu);
        return true;
    }

    public byte[] read(File file) throws IOException {

        byte[] buffer = new byte[(int) file.length()];
        InputStream ios = null;
        try {
            ios = new FileInputStream(file);
            if (ios.read(buffer) == -1) {
                throw new IOException(
                        "EOF reached while trying to read the whole file");
            }
        } finally {
            try {
                if (ios != null)
                    ios.close();
            } catch (IOException e) {

            }
        }
        return buffer;
    }

    private void uploadFile(ArrayList<File> files) {

        fab.setEnabled(false);
        SharedPreferences serverUrl = PreferenceManager.getDefaultSharedPreferences(this);
        String server = serverUrl.getString(MainActivity.SERVER_URL, "http://56v6f22-l:6868");
        SharedPreferences prefsKeys = getSharedPreferences(MainActivity.DEVICE_KEYS, 0);
        String allKeys = StringUtils.join(prefsKeys.getAll().values(), ",");
        ArrayList<Thread> threads = new ArrayList<>();
        List<Exception> errs =  Collections.synchronizedList(new ArrayList<Exception>());
        for (File f : files) {
            HashMap<String, String> data = new HashMap<>();

            data.put("keys", allKeys);
            try {

                byte[] gzipBytes = read(f);
                String b64gzip = Base64.encodeToString(gzipBytes, Base64.DEFAULT);
                data.put("data", b64gzip);
                System.out.println("Exporting to :" + server + "/api/upload");
                Thread t = performPostCall(server + "/api/upload", data, errs);
                threads.add(t);
            } catch (IOException e) {

            }
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        fab.setEnabled(true);
        String msg;
        if(errs.isEmpty()) {
            msg = String.format("Uploaded %d files", files.size());
        } else {
           msg = "There were errors: "+errs.get(0).getMessage();
        }
        Snackbar.make(mRecyclerView, msg, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }


    /**
     * From: https://stackoverflow.com/questions/2938502/sending-post-data-in-android
     *
     * @param requestURL
     * @param postDataParams
     * @return
     */
    public Thread performPostCall(final String requestURL,
                                  final HashMap<String, String> postDataParams, final List<Exception> errs) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                URL url;
                String response = "";
                try {
                    url = new URL(requestURL);

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(15000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);


                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(os, "UTF-8"));
                    writer.write(getPostDataString(postDataParams));

                    writer.flush();
                    writer.close();
                    os.close();
                    int responseCode = conn.getResponseCode();


                    String line;
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    while ((line = br.readLine()) != null) {
                        response += line;
                    }
                    if (responseCode != HttpsURLConnection.HTTP_OK) {
                        errs.add(new Exception(response));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errs.add(e);
                }


            }
        });
        t.start();
        return t;
    }

    private String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    public void deleteSelected(MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Selected Items");
        final ArrayList<File> toDelete = new ArrayList<>();
        for (int i = 0; i < mListAdapter.getItemCount(); i++) {
            if (mListAdapter.isSelected(i))
                toDelete.add(mListAdapter.getItem(i));
        }
        builder.setMessage(String.format("Are you sure you want to delete %d files?", toDelete.size()));
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // delete the selected items
                for (File f : toDelete) {
                    if (!f.delete()) {
                        System.out.println("Failed to delete: " + f);
                    }
                }
                mListAdapter = new FileListAdapter(mContext);
                mRecyclerView.setAdapter(mListAdapter);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    public void selectAll(MenuItem item) {
        for (int i = 0; i < mListAdapter.getItemCount(); i++) {
            mListAdapter.setSelected(i, true);
        }
    }
}
