package org.dutchaug.ccparty;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.SignInButton;
import com.neenbedankt.bundles.annotation.Frozen;
import com.squareup.otto.Subscribe;

import org.dutchaug.ccparty.util.BusProvider;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class SignInFragment extends Fragment {

    @InjectView(R.id.login)
    SignInButton mSignInButton;
    @InjectView(R.id.login_banner)
    TextView mLoginBanner;
    @InjectView(R.id.progress)
    ProgressBar mSpinner;
    @Frozen
    boolean mDidSignIn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SignInFragmentState.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SignInFragmentState.saveInstanceState(this, outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sign_in, container, false);
        ButterKnife.inject(this, view);
        return view;
    }

    @Override
    public void onStart() {
        BusProvider.getInstance().register(this);
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        BusProvider.getInstance().unregister(this);
    }

    @Subscribe
    public void onSignInEvent(SignInEvent event) {
        if (event.error) {
            mSpinner.setVisibility(View.GONE);
            mLoginBanner.setVisibility(View.VISIBLE);
            mSignInButton.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.login)
    public void performLogin() {
        mDidSignIn = true;
        getContract().onSignInClicked();
    }

    private SignInFragmentContract getContract() {
        return (SignInFragmentContract) getActivity();
    }

    public static interface SignInFragmentContract {
        public void onSignInClicked();
    }

}
