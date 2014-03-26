package org.dutchaug.ccparty;

import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.Listener;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.dutchaug.ccparty.model.Message;
import org.dutchaug.ccparty.util.MediaRouterUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nl.qbusict.cupboard.QueryResultIterable;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;
import static org.dutchaug.ccparty.data.CupboardContentProvider.getUri;

public class CastService extends Service implements ConnectionCallbacks, OnConnectionFailedListener {

    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_SESSION_ID = "session";
    private static final String TAG = "CastService";

    private GoogleApiClient mGoogleApiClient;
    private boolean mUnbound;
    private String mSessionId;
    private MediaRouter mMediaRouter;
    private ExecutorService mThreadPool = Executors.newFixedThreadPool(1);
    private MessageReceivedCallback mMessagesCallback;

    private Listener mListener = new Listener() {
        @Override
        public void onApplicationDisconnected(int statusCode) {
            super.onApplicationDisconnected(statusCode);
            mSessionId = null;
            stopForeground(true);
            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
            stopSelf();
        }
    };

    private ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateMessages();
        }
    };

    private Runnable mMessageSender = new Runnable() {
        @Override
        public void run() {
            QueryResultIterable<Message> itr = cupboard().withContext(getApplicationContext()).query(getUri(Message.class), Message.class).
                    withSelection("pending = 1").query();
            try {
                for (Message m : itr) {
                    sendMessage(m);
                    cupboard().withContext(getApplicationContext()).delete(getUri(Message.class), m);
                }
            } finally {
                itr.close();
            }
        }
    };

    private void sendMessage(Message message) {
        if (mGoogleApiClient.isConnected()) {
            JSONObject m = new JSONObject();
            try {
                m.put("type", "message");
                JSONObject payload = new JSONObject();
                payload.put("message", message.message);
                payload.put("sender", message.sender);
                payload.put("avatar", message.avatar);
                m.put("message", payload);
                // fire and forget
                Cast.CastApi.sendMessage(mGoogleApiClient, PartyMessages.NAMESPACE, m.toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void updateMessages() {
        mThreadPool.submit(mMessageSender);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mMessagesCallback = new PartyMessages(getApplicationContext());
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        getContentResolver().registerContentObserver(Uri.parse("content://org.dutchaug.ccparty.provider"), true, mObserver);
    }

    private void startCasting(CastDevice device) {
        Builder builder = new Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this);

        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(device, mListener);
        builder.addApi(Cast.API, apiOptionsBuilder.build());
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        mGoogleApiClient = builder.build();
        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onStartCommand");
        }

        CastDevice device = intent.getParcelableExtra(EXTRA_DEVICE);

        if (device != null) {
            startCasting(device);
        }
        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onUnbind");
            if (!mGoogleApiClient.isConnected()) {
                stopSelf();
            }
        }
        mUnbound = true;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onRebind");
        }
        mUnbound = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onBind");
        }
        mUnbound = false;
        return new LocalBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onDestroy");
        }
        getContentResolver().unregisterContentObserver(mObserver);
        mThreadPool.shutdownNow();
    }

    @Override
    public void onConnected(Bundle bundle) {
        PendingResult<ApplicationConnectionResult> result;

        if (mSessionId == null) {
            result = Cast.CastApi.launchApplication(mGoogleApiClient, MediaRouterUtil.CAST_APP_ID, false);
        } else {
            result = Cast.CastApi.joinApplication(mGoogleApiClient, MediaRouterUtil.CAST_APP_ID, mSessionId);
        }

        result.setResultCallback(new ResultCallback<ApplicationConnectionResult>() {
            @Override
            public void onResult(ApplicationConnectionResult status) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Cast connection status received, success = " + status.getStatus().isSuccess());
                }
                if (status.getStatus().isSuccess()) {
                    mSessionId = status.getSessionId();
                    setupMessageBus();
                } else {
                    mSessionId = null;
                    mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
                    stopSelf();
                }
            }
        });
    }

    private void setupMessageBus() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mGoogleApiClient, PartyMessages.NAMESPACE, mMessagesCallback);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up message call backs", e);
        }
    }

    @Override
    public void onConnectionSuspended(int status) {
        Log.d(TAG, "Connection suspended, try reconnect");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "Connection failed");
        stopSelf();
    }

    public void stopCasting() {
        try {
            Cast.CastApi.removeMessageReceivedCallbacks(mGoogleApiClient, PartyMessages.NAMESPACE);
        } catch (IOException e) {
            Log.e(TAG, "Error removing callbacks", e);
        }
        Cast.CastApi.sendMessage(mGoogleApiClient, PartyMessages.NAMESPACE, "{ \"type\" : \"bye\" }").setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                mGoogleApiClient.disconnect();
                stopSelf();
            }
        });
    }

    public class LocalBinder extends Binder {
        CastService getService() {
            return CastService.this;
        }
    }
}
