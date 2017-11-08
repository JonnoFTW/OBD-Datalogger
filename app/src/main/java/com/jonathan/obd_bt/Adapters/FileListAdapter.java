package com.jonathan.obd_bt.Adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jonathan.obd_bt.R;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

/**
 * Created by mack0242 on 16/10/17.
 */

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {
    public boolean isSelected(int i) {
        return mDataset[i].isSelected();
    }

    private class FileSelect {
        private boolean isSelected = false;
        private File mFile;

        public FileSelect(File f) {
            mFile = f;
        }

        public void setSelected(boolean b) {
            isSelected = b;
        }

        public boolean isSelected() {
            return isSelected;
        }
    }

    private FileSelect[] mDataset;

    public File getItem(int i) {
        return mDataset[i].mFile;
    }


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView mTextView;
        private TextView mSizeView;
        private CheckBox mCheckbox;
        private LinearLayout mLayout;
        private File file;

        public File getFile() {
            return file;
        }

        public ViewHolder(LinearLayout v) {
            super(v);
            mLayout = v.findViewById(R.id.file_list_ll);
            mCheckbox = v.findViewById(R.id.select_file);
            mSizeView = v.findViewById(R.id.readings_size);
            mTextView = v.findViewById(R.id.readings_textview);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public FileListAdapter(Context context) {
        File filesDir = context.getFilesDir();
        File[] files = filesDir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.contains(".gz");
            }
        });
        mDataset = new FileSelect[files.length];
        Arrays.sort(files);
        for (int i = 0; i < files.length; i++) {
            mDataset[i] = new FileSelect(files[i]);
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public FileListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
        // create a new view
        LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.readings_file_view, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final File f = mDataset[position].mFile;
        holder.mTextView.setText(f.getName());
        holder.mSizeView.setText(String.format("%d bytes", f.length()));
        holder.mCheckbox.setOnCheckedChangeListener(null);
        holder.mCheckbox.setChecked(mDataset[position].isSelected());
        holder.mLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder.mCheckbox.setChecked(!mDataset[position].isSelected());
            }
        });
        holder.mCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDataset[position].setSelected(isChecked);
            }
        });


    }
    public void setSelected(int position, boolean selected) {
        mDataset[position].setSelected(selected);
        notifyItemChanged(position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.length;
    }
}


