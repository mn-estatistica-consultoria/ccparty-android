package org.dutchaug.ccparty.data;

import android.annotation.SuppressLint;
import android.content.UriMatcher;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UriHelper {
    private final UriMatcher mMatcher;
    private final String mAuthority;
    private final List<Class<?>> mEntities = new ArrayList<Class<?>>(20);
    private final List<String> mPaths = new ArrayList<String>(20);
    private final Uri mBaseUri;
    private int mRuleCount = 0;

    public UriHelper(String authority) {
        mAuthority = authority;
        mBaseUri = Uri.parse("content://" + mAuthority);
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    }

    public UriHelper(String authority, Collection<Class<?>> entities) {
        this(authority);
        addAll(entities);
    }

    public void addAll(Collection<Class<?>> entities) {
        for (Class<?> clz : entities) {
            add(clz);
        }
    }

    @SuppressLint("DefaultLocale")
    public void add(Class<?> clz) {
        add(clz, clz.getName().toLowerCase());
    }

    public void add(Class<?> clz, String path) {
        int index = mEntities.indexOf(clz);
        if (index > -1) {
            throw new IllegalStateException("Entity " + clz + " already added.");
        }
        mMatcher.addURI(mAuthority, path, mRuleCount++);
        mMatcher.addURI(mAuthority, path + "/#", mRuleCount++);
        mEntities.add(clz);
        mPaths.add(path);
    }

    public Class<?> getMatchedClass(Uri uri) {
        int index = mMatcher.match(uri);
        if (index > UriMatcher.NO_MATCH) {
            return mEntities.get(index / 2);
        }
        return null;
    }

    public Uri getClassUri(Class<?> clz) {
        int index = mEntities.indexOf(clz);
        if (index > -1) {
            return Uri.parse("content://" + mAuthority + "/" + mPaths.get(index));
        }
        return null;
    }

    public boolean matches(Uri uri) {
        return mMatcher.match(uri) > UriMatcher.NO_MATCH;
    }

    public boolean isCollection(Uri uri) {
        int index = mMatcher.match(uri);
        if (index > UriMatcher.NO_MATCH) {
            return (index % 2) == 0;
        }
        return false;
    }

    public Uri getBaseUri() {
        return mBaseUri;
    }
}