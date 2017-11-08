package com.jonathan.obd_bt.Adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v4.util.Pair;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jonathan.obd_bt.MainActivity;
import com.jonathan.obd_bt.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mack0242 on 17/10/17.
 */

public class KeyAdapter extends RecyclerView.Adapter<KeyAdapter.ViewHolder> {
    private final boolean isDevice;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView mKeyTitle;
        private TextView mKeyValue;
        private View mLayout;

        public ViewHolder(View v) {
            super(v);
            mLayout = v;
            mKeyTitle = v.findViewById(R.id.key_key);
            mKeyValue = v.findViewById(R.id.key_value);
        }
    }

    ArrayList<Pair<String, String>> mData = new ArrayList<>();
    SharedPreferences mPrefs;
    Activity mContext;

    public KeyAdapter(Activity activity, boolean isDevice) {
        mPrefs = activity.getSharedPreferences(isDevice?MainActivity.DEVICE_KEYS:MainActivity.BLUETOOTH_KEYS, 0);
        this.isDevice = isDevice;
        mContext = activity;
        Map<String, ?> allKeys = mPrefs.getAll();
        for (Map.Entry<String, ?> entry : allKeys.entrySet()) {
            mData.add(new Pair<String, String>(entry.getKey(), entry.getValue().toString()));
        }
        Collections.sort(mData, new Comparator<Pair<String, String>>() {
            @Override
            public int compare(Pair<String, String> o1, Pair<String, String> o2) {
                return o1.first.compareTo(o2.first);
            }
        });
    }

    @Override
    public KeyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.key_layout, parent, false);
        v.findViewById(R.id.key_layout);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    public void keyDialog(final boolean existing, final Pair<String, String> data, final int position) {
        final KeyAdapter adapter = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        LayoutInflater inflater = mContext.getLayoutInflater();
        View v = inflater.inflate(R.layout.key_crud_dialog, null);
        final EditText key_input = v.findViewById(R.id.key_key_input);
        if(!isDevice) {
            key_input.setHint(R.string.bluetooth_name_hint);
        }
        final EditText value_input = v.findViewById(R.id.key_value_input);
        builder.setView(v);
        builder
                .setTitle(existing?"Edit Key":"Add Key")
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = mPrefs.edit();

                        editor.putString(existing?data.first:key_input.getEditableText().toString(), value_input.getEditableText().toString());
                        editor.apply();
                        if (existing)
                            adapter.notifyItemChanged(position);
                        else
                            adapter.notifyItemInserted(0);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        if (existing) {
            key_input.setText(data.first);
            value_input.setText(data.second);
            key_input.setEnabled(false);
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.remove(data.first);
                    editor.apply();
                    adapter.notifyItemRemoved(position);
                }
            });
        }
        builder.create().show();

    }

    @Override
    public void onBindViewHolder(KeyAdapter.ViewHolder holder, final int position) {
        final Pair<String, String> data = mData.get(position);
        holder.mKeyTitle.setText(data.first);
        holder.mKeyValue.setText(data.second);

        holder.mLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // show a nice little crud dialog
                keyDialog(true, data, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }
}
