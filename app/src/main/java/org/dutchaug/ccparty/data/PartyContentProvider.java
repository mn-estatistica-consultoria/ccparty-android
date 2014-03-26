package org.dutchaug.ccparty.data;

import org.dutchaug.ccparty.model.Message;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class PartyContentProvider extends CupboardContentProvider {

    private static final int VERSION = 1;

    static {
        cupboard().register(Message.class);
    }

    public PartyContentProvider() {
        // in memory
        super(null, VERSION);
    }
}
