package org.dutchaug.ccparty.util;

import android.support.v7.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;

public class MediaRouterUtil {
    public static final String CAST_APP_ID = "AE783635";


    public static MediaRouteSelector createSelector() {
        return new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CAST_APP_ID))
                .build();
    }
}
