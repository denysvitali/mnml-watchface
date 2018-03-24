package it.denv.mnmlwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.*;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import it.denv.mnmlwatchface.events.MessageEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {
    
    public static final boolean DEBUG = false;
    
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000;
    private static final int MSG_UPDATE_TIME = 0;
    
    private static final int LEFT_DIAL_COMPLICATION = 0;
    private static final int RIGHT_DIAL_COMPLICATION = 1;

    public static final int[] COMPLICATION_IDS = {LEFT_DIAL_COMPLICATION, RIGHT_DIAL_COMPLICATION};
    
    public static final String THEME_CHANGED = "it.denv.mnmlwatchface.THEME_CHANGED";

    // Left and right dial supported types.
    public static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_SHORT_TEXT},
            {ComplicationData.TYPE_SHORT_TEXT}
    };
    private static final String TAG = "mnmlwatchface";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    public class Engine extends CanvasWatchFaceService.Engine {

        private int batteryLevel = -1;
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private final BroadcastReceiver mBatteryLevelReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                float batteryPct = level / (float)scale; // your %
                batteryLevel = (int) (batteryPct * 100);
            }
        };
        private final BroadcastReceiver mThemeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Got a theme event!");
                applyTheme(intent.getIntExtra("theme", R.style.Green));
            }
        };

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onMessageEvent(MessageEvent event) {
            Log.d(TAG, "Message Event" + event.name);
            if(event.name.equals("theme")) {
                applyTheme(event.value);
            }
        };
        
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mRegisteredBatteryReceiver = false;
        private boolean mRegisteredThemeReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private Paint mBackgroundPaint;
        private Paint mTextPaint;
        private Paint mDatePaint;
        private Paint activeChargeSecondsPaint;
        private Paint activeSecondsPaint;
        private Paint inactiveSecondsPaint;
        private Paint inactiveChargeSecondsPaint;
        private Paint mNotificationCountPaint;
        private int mCurrentTheme = R.style.Green;

        // Complications
        private Paint mComplicationPaint;
        private int mComplicationsY;
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;
        
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;
        private Resources.Theme mTheme;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTheme(R.style.Red);
            mTheme = getTheme();
            mTheme.applyStyle(R.style.Red, false);
            
            Resources resources = MyWatchFace.this.getResources();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .setAccentColor(getCustomColor(R.attr.accent))
                    .build());

            mCalendar = Calendar.getInstance();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            
            applyTheme(R.style.Gold);
            createPaint();
            
            setDefaultSystemComplicationProvider(1, SystemProviders.DATE, ComplicationData.TYPE_RANGED_VALUE);
            registerReceiver();
            initializeComplications();
            
        }

        private void createPaint() {
            Resources resources = getApplicationContext().getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    getCustomColor(R.attr.background)
            );


            float textSize = 0;
            float dateSize = 0;
            if(mTextPaint != null){
                textSize = mTextPaint.getTextSize();
            }

            if(mDatePaint != null){
                dateSize = mDatePaint.getTextSize();
            }
            
            // Initializes Watch Face.
            mTextPaint = new Paint();
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setColor(
                    getCustomColor(R.attr.digital_text)
            );
            mTextPaint.setTextSize(textSize);

            mDatePaint = new Paint();
            mDatePaint.setTypeface(Typeface.SANS_SERIF);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setColor(
                    getCustomColor(R.attr.digital_date)
            );
            mDatePaint.setTextSize(dateSize);

            activeChargeSecondsPaint = new Paint();
            activeChargeSecondsPaint.setAntiAlias(true);
            activeChargeSecondsPaint.setColor(
                    getCustomColor(R.attr.active_charge_seconds)
            );

            activeSecondsPaint = new Paint();
            activeSecondsPaint.setAntiAlias(true);
            activeSecondsPaint.setColor(
                    getCustomColor(R.attr.seconds_color)
            );
            activeSecondsPaint.setStrokeWidth(1);

            inactiveSecondsPaint = new Paint();
            inactiveSecondsPaint.setAntiAlias(true);
            inactiveSecondsPaint.setColor(
                    getCustomColor(R.attr.inactive_seconds)
            );
            inactiveSecondsPaint.setStrokeWidth(1);

            inactiveChargeSecondsPaint = new Paint();
            inactiveChargeSecondsPaint.setAntiAlias(true);
            inactiveChargeSecondsPaint.setColor(
                    getCustomColor(R.attr.inactive_charge_seconds)
            );

            mNotificationCountPaint = new Paint();
            mNotificationCountPaint.setTypeface(Typeface.SANS_SERIF);
            mNotificationCountPaint.setAntiAlias(true);
            mNotificationCountPaint.setColor(
                    getCustomColor(R.attr.notification_count)
            );
            mNotificationCountPaint.setStrokeWidth(1);
            mNotificationCountPaint.setTextSize(resources.getDimension(R.dimen.notification_count_size));
        }

        private void initializeComplications() {
            Log.d(TAG, "initializeComplications()");
            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
        
            Resources r = getResources();
            
            mComplicationPaint = new Paint();
            mComplicationPaint.setColor(Color.WHITE);
            mComplicationPaint.setTextSize(r.getDimension(R.dimen.complication_text_size));
            mComplicationPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            mComplicationPaint.setAntiAlias(true);

            setActiveComplications(COMPLICATION_IDS);
        }

        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);
            invalidate();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            registerTimeZoneReceiver();
            registerBatteryReceiver();
            registerThemeReceiver();
        }
        
        private void registerTimeZoneReceiver(){
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }
        
        private void registerBatteryReceiver(){
            if (mRegisteredBatteryReceiver) {
                return;
            }
            mRegisteredBatteryReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            MyWatchFace.this.registerReceiver(mBatteryLevelReceiver, filter);
        }

        private void registerThemeReceiver(){
            if (mRegisteredThemeReceiver) {
                return;
            }
            mRegisteredThemeReceiver = true;
            EventBus.getDefault().register(this);
        }

        private void unregisterReceiver() {
            unregisterTimeZoneReceiver();
            unregisterBatteryReceiver();
            //unregisterThemeReceiver();
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void unregisterBatteryReceiver() {
            if (!mRegisteredBatteryReceiver) {
                return;
            }
            mRegisteredBatteryReceiver = false;
            MyWatchFace.this.unregisterReceiver(mBatteryLevelReceiver);
        }
        
        private void unregisterThemeReceiver() {
            if (!mRegisteredThemeReceiver) {
                return;
            }
            mRegisteredThemeReceiver = false;
            EventBus.getDefault().unregister(this);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
                mDatePaint.setAntiAlias(!inAmbientMode);
                mNotificationCountPaint.setAntiAlias(!inAmbientMode);
                mComplicationPaint.setAntiAlias(!inAmbientMode);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    //mBackgroundPaint.setColor(Color.RED);
                    break;
            }
            invalidate();
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder,
                                     int format,
                                     int width,
                                     int height)
        {
            mComplicationsY = (int) ((height / 2) + (mComplicationPaint.getTextSize() / 2));
        }
        
        public @ColorInt
        int getCustomColor(int id){
            TypedValue typedValue = new TypedValue();
            mTheme.resolveAttribute(id, typedValue, true);
            return typedValue.data;
        }


        private void applyTheme(int theme) {
            mCurrentTheme = theme;
            mTheme.applyStyle(theme, true);
            createPaint();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            /*if(mCurrentTheme == R.style.Green){
                applyTheme(R.style.Red);
            } else {
                applyTheme(R.style.Green);
            }*/
            
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mTextPaint.setColor(getCustomColor(R.attr.ambient_mode_text));
                mDatePaint.setColor(getCustomColor(R.attr.ambient_mode_date));
                mNotificationCountPaint.setColor(
                        getCustomColor(R.attr.ambient_mode_notification_count)
                );
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                mTextPaint.setColor(getCustomColor(R.attr.digital_text));
                mDatePaint.setColor(getCustomColor(R.attr.digital_date));
                mNotificationCountPaint.setColor(getCustomColor(R.attr.notification_count));
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            /*String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));*/
            
            int hour = mCalendar.get(Calendar.HOUR);
            hour = hour == 0 ? 12 : hour;
            
            String month = new SimpleDateFormat("MMM dd").format(mCalendar.getTime());
            String text = String.format("%d:%02d", hour, mCalendar.get(Calendar.MINUTE));
            String date = month;
            String seconds_text = String.format("%02d", mCalendar.get(Calendar.SECOND));
            int seconds_now = mCalendar.get(Calendar.SECOND);
            int millis_now = mCalendar.get(Calendar.MILLISECOND);
            
            Resources res = getResources();
            
            Rect b = new Rect();
            Rect b2 = new Rect();
            mTextPaint.getTextBounds(text, 0, text.length(), b);
            mDatePaint.getTextBounds(date, 0, date.length(), b2);
            
            int spacing = (int) spToPx(60, getApplicationContext());
            
            float seconds_lm = res.getDimension(R.dimen.seconds_lm);
            float offsetX = bounds.centerX() - mTextPaint.measureText(text)/2;
            float offsetY = bounds.centerY() + b.height()/2;
            
            if(DEBUG) {
                Paint testPaint = new Paint();
                testPaint.setColor(Color.RED);

                canvas.drawLine(0, bounds.centerY(), bounds.right, bounds.centerY(), testPaint);
            }
            
            if(!mAmbient) {
                //canvas.drawText(seconds_text, offsetX + b.width() + seconds_lm, offsetY, mSecondsPaint);
                
                float p_l = (float) bounds.left;
                float p_r = bounds.right;
                float p_t = bounds.top;
                float p_b = bounds.bottom;
                
                //canvas.drawArc(p_l, p_t, p_r, p_b, (float) -90, (float) (360.0/(60) * (seconds_now + (0.001) * millis_now)), true, activeSecondsPaint);
                //canvas.drawCircle(bounds.centerX(), bounds.centerY(), 10, activeSecondsPaint);
                
                float radius = bounds.width()/2 - 15;
                
                for(int i = 0; i < 60 ; i++){
                    double angle =  - 2 * Math.PI / 59 * i + Math.PI;
                    float x = (float) (radius * Math.sin(angle));
                    float y = (float) (radius * Math.cos(angle));
                    canvas.drawCircle(bounds.centerX() + x,bounds.centerY() + y, 5, 
                            seconds_now >= i ? 
                                    (batteryLevel*60/100 >= i ? activeChargeSecondsPaint : activeSecondsPaint) 
                                    : 
                                    ((batteryLevel*60/100 >= i) ? inactiveChargeSecondsPaint : inactiveSecondsPaint)
                    );
                }
            }


            String unread_text = String.format("%d - %d%%", getUnreadCount(), batteryLevel);
            
            Rect b3 = new Rect();
            mNotificationCountPaint.getTextBounds(unread_text, 0, text.length(), b3);
            
            canvas.drawText(
                    unread_text, 
                    bounds.centerX() - mNotificationCountPaint.measureText(unread_text)/2,
                    bounds.centerY() - spacing + b3.height(), mNotificationCountPaint
            );
            
            canvas.drawText(text,
                    offsetX, 
                    offsetY, 
                    mTextPaint
            );
            
            
            canvas.drawText(
                    date,
                    bounds.centerX() - mDatePaint.measureText(date)/2,
                    bounds.centerY() + b2.height() + spacing, 
                    mDatePaint
            );
        }
        
        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        public int spToPx(float sp, Context context) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
        }
        
        
        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
