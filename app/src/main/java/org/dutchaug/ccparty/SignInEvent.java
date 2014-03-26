package org.dutchaug.ccparty;

public class SignInEvent {
    public final boolean error;

    public SignInEvent(boolean error) {
        this.error = error;
    }
}
