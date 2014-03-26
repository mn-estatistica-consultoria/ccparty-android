package org.dutchaug.ccparty;

import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.Callback;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.neenbedankt.bundles.annotation.Frozen;

import org.dutchaug.ccparty.CastService.LocalBinder;
import org.dutchaug.ccparty.DiscoveryFragment.DiscoveryFragmentContract;
import org.dutchaug.ccparty.MessagesFragment.MessagesFragmentContract;
import org.dutchaug.ccparty.SignInFragment.SignInFragmentContract;
import org.dutchaug.ccparty.model.Message;
import org.dutchaug.ccparty.util.BusProvider;
import org.dutchaug.ccparty.util.MediaRouterUtil;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;
import static org.dutchaug.ccparty.data.CupboardContentProvider.getUri;


public class MainActivity extends ActionBarActivity implements ConnectionCallbacks, OnConnectionFailedListener, SignInFragmentContract, DiscoveryFragmentContract, MessagesFragmentContract {

    private static final int RC_SIGN_IN = 1;
    private static final String TAG = "MainActivity";
    private Callback mMediaCallback = new Callback() {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            super.onRouteAdded(router, route);
            Log.d(TAG, "Added route: " + route.getName());
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            super.onRouteRemoved(router, route);
            Log.d(TAG, "Removed route: " + route.getName());
        }

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo route) {
            Log.d(TAG, "Route selected");
            super.onRouteSelected(router, route);
            startCasting(CastDevice.getFromBundle(route.getExtras()));

        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo route) {
            super.onRouteUnselected(router, route);
            stopCasting();
        }
    };
    @Frozen
    boolean mIntentInProgress;

    @Frozen
    String mDisplayName;
    @Frozen
    String mProfileUrl;
    @Frozen
    boolean mDidSignIn = false;
    @Frozen
    String mFirstName;
    @Frozen
    boolean mDeviceDiscovered;
    @Frozen
    boolean mCasting = false;
    private GoogleApiClient mGoogleApiClient;
    private MenuItem mSignOutMenu;
    private MenuItem mediaRouteMenuItem;
    private ConnectionResult mLastConnectionResult;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private CastService mCastService;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mCastService = ((LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mCastService = null;
        }
    };

    private void startCasting(CastDevice device) {
        Intent intent = new Intent(this, CastService.class);
        intent.putExtra(CastService.EXTRA_DEVICE, device);
        startService(intent);
        bindService(intent, mServiceConnection, 0);
    }

    private void stopCasting() {
        if (mCastService != null) {
            mCastService.stopCasting();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_fragment);
        MainActivityState.restoreInstanceState(this, savedInstanceState);

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = MediaRouterUtil.createSelector();

        createGoogleApiClient();

        if (savedInstanceState == null) {
            if (!mMediaRouter.isRouteAvailable(mMediaRouteSelector, 0)) {
                showDiscovery();
            } else {
                mDeviceDiscovered = true;
                showSignIn();
            }
        }

        Intent intent = new Intent(this, CastService.class);
        if (bindService(intent, mServiceConnection, 0)) {
            Log.i(TAG, "Bound to service");
        }
    }

    private void showSignIn() {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new SignInFragment()).commit();
        mGoogleApiClient.connect();
    }

    private void showDiscovery() {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new DiscoveryFragment()).commit();
    }

    private void createGoogleApiClient() {
        Builder builder = new Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE);

        mGoogleApiClient = builder.build();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        MainActivityState.saveInstanceState(this, outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mDeviceDiscovered) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(mServiceConnection);
        } catch (Exception ex) {
            // ignore;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Connected");
        if (!mDidSignIn || mProfileUrl == null) {
            invalidateOptionsMenu();
            mDidSignIn = true;
            Person person = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            if (person == null) {
                Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
                return;
            }
            mDisplayName = person.getDisplayName();
            mProfileUrl = Uri.parse(person.getImage().getUrl()).buildUpon().clearQuery().build().toString();
            mFirstName = person.getName().getGivenName();
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new MessagesFragment()).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMediaRouter.removeCallback(mMediaCallback);
    }

    @Override
    public void onConnectionSuspended(int reason) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed");
        BusProvider.getInstance().post(new SignInEvent(true));
        mLastConnectionResult = connectionResult;
        if (mDidSignIn) {
            resolveSignIn();
        }
    }

    private void resolveSignIn() {
        if (!mIntentInProgress && mLastConnectionResult != null && mLastConnectionResult.hasResolution()) {
            try {
                mIntentInProgress = true;
                mLastConnectionResult.startResolutionForResult(this, RC_SIGN_IN);
            } catch (SendIntentException e) {
                mIntentInProgress = false;
                mGoogleApiClient.connect();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            mIntentInProgress = false;
            if (resultCode != RESULT_OK) {
                mDidSignIn = false;
            }
            if (!mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mSignOutMenu.setVisible(mGoogleApiClient.isConnected());
        mediaRouteMenuItem.setVisible(mDidSignIn);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_sign_out:
                signOut();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        if (mGoogleApiClient.isConnected()) {
            mDidSignIn = false;
            mProfileUrl = null;
            mDisplayName = null;
            mFirstName = null;
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment, new SignInFragment()).setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit();
            PendingResult<Status> result = Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
            result.setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    mGoogleApiClient.reconnect();
                    invalidateOptionsMenu();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mSignOutMenu = menu.findItem(R.id.action_sign_out);
        mediaRouteMenuItem = menu.findItem(R.id.action_media_route);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    @Override
    public void onSignInClicked() {
        mDidSignIn = true;
        resolveSignIn();
    }

    @Override
    public void onChromecastFound() {
        mDeviceDiscovered = true;
        showSignIn();
    }

    @Override
    public void onSendMessage(String message) {
        if (!TextUtils.isEmpty(message.trim())) {
            final Message m = new Message();
            m.pending = true;
            m.sender = mDisplayName;
            m.avatar = mProfileUrl;
            m.message = message;
            m.timestamp = System.currentTimeMillis();
            new Thread() {
                @Override
                public void run() {
                    cupboard().withContext(MainActivity.this).put(getUri(Message.class), Message.class, m);
                }
            }.start();
        }
    }
}
