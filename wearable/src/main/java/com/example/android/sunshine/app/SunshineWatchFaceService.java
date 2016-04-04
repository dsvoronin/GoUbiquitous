/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint bgPaint;
        Paint timePaint;
        Paint datePaint;
        Paint tempPaint;
        boolean mAmbient;
        Calendar calendar;

        int tempMax;
        int tempMin;

        Bitmap weatherImage;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.clear(Calendar.ZONE_OFFSET);
                calendar.setTimeInMillis(System.currentTimeMillis());
            }
        };

        final BroadcastReceiver weatherUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                tempMax = intent.getIntExtra(WearableConstants.KEY_TEMP_MAX, -1);
                tempMin = intent.getIntExtra(WearableConstants.KEY_TEMP_MIN, -1);

                weatherImage = intent.getParcelableExtra(WearableConstants.KEY_IMAGE);

                Log.d("SunshineWatchFace", "Broadcast received: [max=" + tempMax + ";min=" + tempMin + "]");
            }
        };

        int mTapCount;

        float mXOffset;
        float mYOffset;

        float dateXOffset;
        float dateYOffset;

        float tempXOffset;
        float tempYOffset;

        float lineWidth;
        float lineYOffset;

        int defaultMargin;
        int centerLineOffset;
        int iconSize;
        int tempHorizontalOffset;

        DateFormat format = new SimpleDateFormat("EEE, MMM dd yyyy");

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            LocalBroadcastManager.getInstance(SunshineWatchFaceService.this.getBaseContext())
                    .registerReceiver(weatherUpdateReceiver, new IntentFilter(WearableConstants.WEATHER_UPDATE_ACTION));

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineWatchFaceService.this.getResources();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            dateYOffset = resources.getDimension(R.dimen.date_y_offset);

            tempYOffset = resources.getDimension(R.dimen.temp_y_offset);

            lineWidth = resources.getDimension(R.dimen.line_width);
            lineYOffset = resources.getDimension(R.dimen.line_y_offset);

            iconSize = (int) resources.getDimension(R.dimen.icon_size);
            tempHorizontalOffset = (int) resources.getDimension(R.dimen.temp_horizontal_offset);

            bgPaint = new Paint();
            bgPaint.setColor(resources.getColor(R.color.background));

            timePaint = new Paint();
            timePaint = createTextPaint(resources.getColor(R.color.digital_text));

            datePaint = createTextPaint(resources.getColor(R.color.digital_text_light));

            tempPaint = createTextPaint(resources.getColor(R.color.digital_text_light));

            calendar = new GregorianCalendar();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            LocalBroadcastManager.getInstance(SunshineWatchFaceService.this.getBaseContext()).unregisterReceiver(weatherUpdateReceiver);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                calendar.setTimeZone(TimeZone.getDefault());
                calendar.setTimeInMillis(System.currentTimeMillis());
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            timePaint.setTextSize(textSize);

            datePaint.setTextSize(resources.getDimension(isRound ? R.dimen.date_text_size_round : R.dimen.date_text_size));
            dateXOffset = resources.getDimension(isRound ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);

            tempXOffset = resources.getDimension(isRound ? R.dimen.temp_x_offset_round : R.dimen.temp_x_offset);

            defaultMargin = (int) resources.getDimension(isRound ? R.dimen.default_margin : R.dimen.default_margin);
            centerLineOffset = (int) resources.getDimension(isRound ? R.dimen.center_line_offset : R.dimen.center_line_offset);

            tempPaint.setTextSize(resources.getDimension(isRound ? R.dimen.temp_text_size_round : R.dimen.temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    timePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            drawBackground(canvas, bounds);

            int lineY = drawLine(canvas, bounds);

            calendar.setTimeInMillis(System.currentTimeMillis());

            int dateYTop = drawDate(canvas, bounds, lineY);

            drawTime(canvas, bounds, dateYTop);

            int tempBaseLine = lineY + textHeightInPx(tempPaint);

            int left = drawTemperature(canvas, bounds, tempBaseLine);

            if (weatherImage != null) {
                Rect imageBound = new Rect(left - iconSize, lineY, left, lineY + iconSize);
                canvas.drawBitmap(weatherImage, null, imageBound, null);
            }
        }

        private void drawBackground(Canvas canvas, Rect bounds) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), bgPaint);
            }
        }

        /**
         * @return line y
         */
        private int drawLine(Canvas canvas, Rect bounds) {
            int xStart = (int) (bounds.centerX() - lineWidth / 2);
            int xEnd = (int) (bounds.centerX() + lineWidth / 2);
            int lineY = bounds.centerY() + centerLineOffset;
            canvas.drawLine(xStart, lineY, xEnd, lineY, datePaint);
            return lineY;
        }

        /**
         * @return date top line y
         */
        private int drawDate(Canvas canvas, Rect bounds, int lineY) {
            int dateY = lineY - defaultMargin;

            String date = format.format(new Date(calendar.getTimeInMillis()));
            canvas.drawText(date, bounds.centerX(), dateY, datePaint);

            return dateY - textHeightInPx(datePaint);
        }

        private void drawTime(Canvas canvas, Rect bounds, int dateTopY) {
            String time = String.format("%d:%02d", calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE));
            canvas.drawText(time, bounds.centerX(), dateTopY, timePaint);
        }

        /**
         * @return left X of temp text
         */
        private int drawTemperature(Canvas canvas, Rect bounds, int yBaseline) {
            String temp = String.format("%d\u00B0 - %d\u00B0", tempMax, tempMin);
            int x = bounds.centerX() + tempHorizontalOffset;
            canvas.drawText(temp, x, yBaseline, tempPaint);
            return x - (int) (tempPaint.measureText(temp) / 2);
        }

        private int textHeightInPx(Paint paint) {
            Paint.FontMetricsInt p = paint.getFontMetricsInt();
            return Math.abs(p.ascent) + Math.abs(p.descent);
        }

        private void drawDebugBaseLine(Canvas canvas, Rect bounds, int yBaseline) {
            if (BuildConfig.DEBUG) {
                canvas.drawLine(0, yBaseline, bounds.right, yBaseline, datePaint);
            }
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
