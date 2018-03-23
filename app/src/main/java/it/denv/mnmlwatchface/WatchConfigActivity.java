package it.denv.mnmlwatchface;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.widget.TextView;

public class WatchConfigActivity extends WearableActivity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_config);

        mTextView = (TextView) findViewById(R.id.text);

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

   
}
