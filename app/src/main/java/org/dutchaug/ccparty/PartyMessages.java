package org.dutchaug.ccparty;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;

import org.dutchaug.ccparty.model.Message;
import org.json.JSONException;
import org.json.JSONObject;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;
import static org.dutchaug.ccparty.data.CupboardContentProvider.getUri;

public class PartyMessages implements MessageReceivedCallback {
    public static final String NAMESPACE = "urn:x-cast:org.dutchaug.ccparty";
    private static final String TAG = "PartyMessages";
    private final Context mContext;

    public PartyMessages(Context context) {
        this.mContext = context;
    }

    @Override
    public void onMessageReceived(CastDevice device, String namespace, String message) {
        Log.i(TAG, "Got message: " + message);
        try {
            JSONObject obj = new JSONObject(message);
            if ("message".equals(obj.optString("type"))) {
                JSONObject messageObj = obj.getJSONObject("message");
                final Message m = new Message();
                m.avatar = messageObj.optString("avatar");
                m.message = messageObj.optString("message");
                m.timestamp = System.currentTimeMillis();
                m.sender = messageObj.optString("sender");
                m.pending = false;
                new Thread() {
                    @Override
                    public void run() {
                        cupboard().withContext(mContext).put(getUri(Message.class), Message.class, m);
                    }
                }.start();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
