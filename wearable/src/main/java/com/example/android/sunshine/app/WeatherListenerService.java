package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.app.WearableConstants.KEY_IMAGE;
import static com.example.android.sunshine.app.WearableConstants.KEY_TEMP_MAX;
import static com.example.android.sunshine.app.WearableConstants.KEY_TEMP_MIN;
import static com.example.android.sunshine.app.WearableConstants.WEATHER_UPDATE_ACTION;

public class WeatherListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WeatherListenerService";
    private static final int TIMEOUT_MS = 2000;

    private GoogleApiClient mGoogleApiClient;

    private LocalBroadcastManager broadcastManager;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Created");

        mGoogleApiClient = new GoogleApiClient.Builder(this.getApplicationContext())
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        Log.i(TAG, "peer connected");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.i(TAG, "onMessageReceived: " + messageEvent.getPath() + " " + messageEvent.getData() + " for " + getPackageName());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i(TAG, "onDataChanged: " + dataEvents + " for " + getPackageName());
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                updateWeather(event.getDataItem());
            }
        }
    }

    private void updateWeather(DataItem item) {
        DataMapItem mapDataItem = DataMapItem.fromDataItem(item);
        DataMap data = mapDataItem.getDataMap();

        Log.i(TAG, "updateWeather: " + data);

        broadcastManager.sendBroadcast(
                new Intent(WEATHER_UPDATE_ACTION)
                        .putExtra(KEY_TEMP_MAX, data.getInt(KEY_TEMP_MAX))
                        .putExtra(KEY_TEMP_MIN, data.getInt(KEY_TEMP_MIN))
                        .putExtra(KEY_IMAGE, loadBitmapFromAsset(data.getAsset(KEY_IMAGE))));
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

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result = mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
