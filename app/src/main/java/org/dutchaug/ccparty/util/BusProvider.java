package org.dutchaug.ccparty.util;

import com.squareup.otto.Bus;

public class BusProvider {
    private static final Bus sBus = new Bus();

    public static final Bus getInstance() {
        return sBus;
    }
}
