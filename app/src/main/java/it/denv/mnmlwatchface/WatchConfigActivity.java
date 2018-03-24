package it.denv.mnmlwatchface;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class WatchConfigActivity extends WearableActivity {

    private TextView mTextView;
    private ListView mListView;
    private SimpleCursorAdapter mCursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_config);
        
        String[] entries = {"Colors", "Features"};
        int[] iconsArray = {R.drawable.ic_color_lens, R.drawable.ic_config};
        Intent[] intents = {
                new Intent(this, WatchConfigColorActivity.class),
                new Intent(this, WatchConfigSettingsActivity.class),
        };

        ConfigListAdapter adapter = new ConfigListAdapter(this,
                entries, iconsArray, intents);
        
        mTextView = (TextView) findViewById(R.id.text);
        mListView = findViewById(R.id.config_lv);
        mListView.setAdapter(adapter);

        
        // Enables Always-on
        setAmbientEnabled();
    }

    private final class ComplicationItem {
        ComponentName watchFace;
        int complicationId;
        int[] supportedTypes;
        Drawable icon;
        String title;

        public ComplicationItem(ComponentName watchFace, int complicationId, int[] supportedTypes,
                                Drawable icon, String title) {
            this.watchFace = watchFace;
            this.complicationId = complicationId;
            this.supportedTypes = supportedTypes;
            this.icon = icon;
            this.title = title;
        }
    }
    
    private class ConfigListAdapter extends BaseAdapter {
        Context mContext;
        String[] mStrings;
        int[] mImages;
        Intent[] mIntents;
        
        public ConfigListAdapter(Context context, String[] text, int[] imageIds, Intent[] intents)
        {
            mContext = context;
            mStrings = text;
            mImages = imageIds;
            mIntents = intents;
        }

        @Override
        public int getCount() {
            return mStrings.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = getLayoutInflater();
            View row = inflater.inflate(R.layout.config_lv_row, parent, false);
            TextView tv = row.findViewById(R.id.row_item_tv);
            ImageView iv = row.findViewById(R.id.row_item_image);
            
            tv.setText(mStrings[position]);
            iv.setImageResource(mImages[position]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(mIntents[position]);
                }
            });
            
            
            return row;
        }
    }

   
}
