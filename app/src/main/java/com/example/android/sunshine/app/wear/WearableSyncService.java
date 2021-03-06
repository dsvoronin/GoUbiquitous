package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

public class WearableSyncService extends IntentService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WearableSyncService";

    private static final long CONNECTION_TIME_OUT_MS = 1000;

    private static final String[] WEAR_COLUMNS = new String[]{
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
    };

    private static final int INDEX_MAX_TEMP = 0;
    private static final int INDEX_MIN_TEMP = 1;
    private static final int INDEX_WEATHER_ID = 2;

    private GoogleApiClient mGoogleApiClient;

    public WearableSyncService() {
        super("WearableSyncService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "Connecting to wearable");

        mGoogleApiClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);

        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(location, System.currentTimeMillis());
        Cursor cursor = getContentResolver().query(weatherForLocationUri, WEAR_COLUMNS, null, null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (cursor.moveToFirst()) {
            int maxTemp = cursor.getInt(INDEX_MAX_TEMP);
            int minTemp = cursor.getInt(INDEX_MIN_TEMP);
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);

            Asset weatherImage = createAssetFromBitmap(
                    bitmapFromDrawableId(getBaseContext(),
                            Utility.getIconResourceForWeatherCondition(weatherId)));

            final PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(CompanionConstants.WEATHER_DATA_ITEM_PREFIX);
            DataMap data = putDataMapRequest.getDataMap();
            data.putLong("timestamp", System.currentTimeMillis());
            data.putInt(CompanionConstants.KEY_MAX_TEMP, maxTemp);
            data.putInt(CompanionConstants.KEY_MIN_TEMP, minTemp);
            data.putAsset(CompanionConstants.KEY_IMAGE, weatherImage);

            if (mGoogleApiClient.isConnected()) {
                Log.i(TAG, "Sending data: " + data.toString());
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest()).await();
                Log.i(TAG, "Data sent");
            } else {
                Log.e(TAG, "Failed to send data item: " + data.toString() + " - Client disconnected from Google Play Services");
            }
        }
        cursor.close();

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected");
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed: " + result.toString());
    }

    private static Bitmap bitmapFromDrawableId(Context context, @DrawableRes int drawableId) {
        return BitmapFactory.decodeResource(context.getResources(), drawableId);
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }
}
