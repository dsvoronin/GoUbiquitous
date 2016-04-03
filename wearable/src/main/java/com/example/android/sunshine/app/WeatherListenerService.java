package com.example.android.sunshine.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import static com.example.android.sunshine.app.WearableConstants.KEY_TEMP_MAX;
import static com.example.android.sunshine.app.WearableConstants.KEY_TEMP_MIN;
import static com.example.android.sunshine.app.WearableConstants.WEATHER_UPDATE_ACTION;

public class WeatherListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WeatherListenerService";

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
                        .putExtra(KEY_TEMP_MIN, data.getInt(KEY_TEMP_MIN)));
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
}
