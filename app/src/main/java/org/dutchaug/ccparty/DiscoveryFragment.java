package org.dutchaug.ccparty;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.Callback;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.dutchaug.ccparty.util.MediaRouterUtil;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class DiscoveryFragment extends Fragment {

    @InjectView(R.id.progress)
    View mProgress;
    @InjectView(R.id.message)
    TextView mMessage;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mSelector;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaRouter = MediaRouter.getInstance(getActivity().getApplicationContext());
        mSelector = MediaRouterUtil.createSelector();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.discovery, container, false);
        ButterKnife.inject(this, view);
        mMessage.setText(R.string.locating_chromecast);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mMediaRouter.isRouteAvailable(mSelector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE)) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mMessage.setText(R.string.keep_looking_chromecast);
                }
            }, 5000);
            mMediaRouter.addCallback(mSelector, mCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        } else {
            ((DiscoveryFragmentContract) getActivity()).onChromecastFound();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mMediaRouter.removeCallback(mCallback);
    }

    private Callback mCallback = new Callback() {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            super.onRouteAdded(router, route);
            if (route.matchesSelector(mSelector)) {
                mHandler.removeCallbacksAndMessages(null);
                mMessage.setText(R.string.chromecast_found);
                mMediaRouter.removeCallback(mCallback);
                ((DiscoveryFragmentContract) getActivity()).onChromecastFound();
            }
        }
    };

    public static interface DiscoveryFragmentContract {
        public void onChromecastFound();
    }


}
