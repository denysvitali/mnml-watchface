package it.denv.mnmlwatchface;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import it.denv.mnmlwatchface.events.MessageEvent;
import org.greenrobot.eventbus.EventBus;

public class WatchConfigColorActivity extends WearableActivity {
    private TextView mTextView;
    private ListView mListView;
    private Resources.Theme mTheme;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_color_config);

        String[] entries = {
                "Green",
                "Red",
                "Violet",
                "Blue",
                "Orange",
                "Green Sea",
                "Gold"
        };
        int[] themes = {
                R.style.Green,
                R.style.Red,
                R.style.Violet,
                R.style.Blue,
                R.style.Orange,
                R.style.GreenSea,
                R.style.Gold
        };
        
        String[] colors = new String[themes.length];
        for(int i = 0; i<themes.length; i++){
            setTheme(themes[i]);
            mTheme = getTheme();
            colors[i] = "#" + Integer.toHexString(getCustomColor(R.attr.primary));
        }
        
        ColorListAdapter adapter = new ColorListAdapter(this,
                entries, colors, themes);

        mTextView = (TextView) findViewById(R.id.text);
        mListView = findViewById(R.id.config_lv);
        mListView.setAdapter(adapter);


        // Enables Always-on
        setAmbientEnabled();
    }

    public @ColorInt
    int getCustomColor(int id){
        TypedValue typedValue = new TypedValue();
        mTheme.resolveAttribute(id, typedValue, true);
        return typedValue.data;
    }

    private class ColorListAdapter extends BaseAdapter {
        Context mContext;
        String[] mStrings;
        String[] mColors;
        int[] mThemes;

        public ColorListAdapter(Context context, String[] text, String[] colors, int[] themes)
        {
            mContext = context;
            mStrings = text;
            mColors = colors;
            mThemes = themes;
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
            View row = inflater.inflate(R.layout.config_color_lv_row, parent, false);
            TextView tv = row.findViewById(R.id.row_item_tv);
            ImageView iv = row.findViewById(R.id.row_item_image);

            Bitmap bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.parseColor(mColors[position]));
            c.drawBitmap(bmp, 0, 0, null);
            
            tv.setText(mStrings[position]);
            iv.setImageBitmap(bmp);
            iv.setClipToOutline(true);
            iv.setBackgroundResource(R.drawable.round_layout);
            //iv.setBackgroundColor(mColors[position]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MyWatchFace.THEME_CHANGED);
                    intent.putExtra("theme", mThemes[position]);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                    MessageEvent msgEvent = new MessageEvent();
                    msgEvent.name = "theme";
                    msgEvent.value = mThemes[position];
                    EventBus.getDefault().post(msgEvent);
                    finish();
                }
            });


            return row;
        }
    }
}
