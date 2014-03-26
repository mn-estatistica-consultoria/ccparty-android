package org.dutchaug.ccparty;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.squareup.picasso.Picasso;

import org.dutchaug.ccparty.model.Message;

import butterknife.ButterKnife;
import butterknife.InjectView;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;
import static org.dutchaug.ccparty.data.CupboardContentProvider.getUri;

public class MessagesFragment extends ListFragment implements LoaderCallbacks<Cursor> {

    @InjectView(R.id.message)
    EditText mMessage;
    private InputMethodManager mInputMethodManager;
    private MessagesAdapter mMessagesAdapter;
    private boolean mAutoScroll;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMessagesAdapter = new MessagesAdapter(getActivity());
        mInputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chat, container, false);
        ButterKnife.inject(this, view);
        mMessage.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                ((MessagesFragmentContract) getActivity()).onSendMessage(v.getText().toString());
                v.setText(null);
                mInputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
        });
        return view;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        CursorLoader loader = new CursorLoader(getActivity());
        loader.setUri(getUri(Message.class));
        loader.setSelection("pending = 0");
        return loader;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setListAdapter(mMessagesAdapter);
        getListView().setStackFromBottom(true);
        getListView().setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mAutoScroll = firstVisibleItem + visibleItemCount == totalItemCount;
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        mMessagesAdapter.swapCursor(data);
        if (mAutoScroll) {
            getListView().post(new Runnable() {
                @Override
                public void run() {
                    setSelection(data.getCount());
                }
            });
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mMessagesAdapter.swapCursor(null);
    }

    public static interface MessagesFragmentContract {
        public void onSendMessage(String message);
    }

    static class MessagesAdapter extends CursorAdapter {

        private final LayoutInflater mInflater;

        public MessagesAdapter(Context context) {
            super(context, null, 0);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.chat_row, parent, false);
            ViewHolder holder = new ViewHolder();
            ButterKnife.inject(holder, view);
            view.setTag(holder);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();
            Message message = cupboard().withCursor(cursor).get(Message.class);
            holder.text.setText(message.message);
            Picasso.with(context).load(Uri.parse(message.avatar).buildUpon().clearQuery().appendQueryParameter("sz", "128").build()).into(holder.avatar);
        }

        class ViewHolder {
            @InjectView(android.R.id.text1)
            TextView text;
            @InjectView(R.id.avatar)
            ImageView avatar;
        }
    }
}
