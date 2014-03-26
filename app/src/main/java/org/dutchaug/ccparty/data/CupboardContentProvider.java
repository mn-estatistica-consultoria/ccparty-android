package org.dutchaug.ccparty.data;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

import nl.qbusict.cupboard.Cupboard;
import nl.qbusict.cupboard.DatabaseCompartment.QueryBuilder;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

/**
 * Content provider that reduces boilerplate for the common cases:
 * <ul>
 * <li>Defines a content uri for a single entity and collection (query) of entities</li>
 * <li>Holds a static UriHelper to map entities to uri in a single place</li>
 * <li>Returns a cursor that notifies on changes to the dataset</li>
 * <li>Provides a callback to control notification with optional sync to network</li>
 * <li>Has a helper for for finding an existing entity based on an external id for inserts (e.g. update with external key)</li>
 * </ul>
 * <p/>
 * This class has a static convenience getUri() function to map an entity to an (generated) uri. To initialize Cupboard and the UriHelper,
 * initialize both in a static block like this:
 * <code>
 * static {
 * // configure the global cupboard instance, this is usually the one returned from CupboardFactory.cupboard(), but anything goes.
 * cupboard().register(MyEntity.class);
 * }
 * </code>
 */
public abstract class CupboardContentProvider extends SQLiteContentProvider {

    private static final String DEFAULT_DATABASE_NAME = "cupboard.db";
    private static final int DEFAULT_DATABASE_VERSION = 1;

    private static UriHelper sUriHelper;

    private final String mDatabaseName;
    private final int mDatabaseVersion;
    protected Cupboard mCupboard = cupboard();

    protected CupboardContentProvider(String databaseName, int databaseVersion) {
        mDatabaseName = databaseName;
        mDatabaseVersion = databaseVersion;
        mCupboard = createCupboard();
    }

    protected CupboardContentProvider(int databaseVersion) {
        this(DEFAULT_DATABASE_NAME, databaseVersion);
    }

    /**
     * Get an uri for the specified entity, using the UriHelper that was initialized from initUriHelper()
     *
     * @param entity the entity to get the uri for
     * @return the uri or null if the entity is not mapped
     */
    public static Uri getUri(Class<?> entity) {
        if (sUriHelper == null) {
            throw new IllegalStateException("uri helper is not initialized");
        }
        return sUriHelper.getClassUri(entity);
    }

    /**
     * Equivalent of {SQLiteOpenHelper#onUpgradeDatabase} when using the built in database helper, invoked after the tables
     * are upgraded by Cupboard
     *
     * @param db         the database
     * @param oldVersion the old version of the database
     * @param newVersion the new version of the database
     */
    protected void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /**
     * Equivalent of {SQLiteOpenHelper#onCreateDatabase} when using the built in database helper, invoked after the tables
     * are created by Cupboard
     *
     * @param db the database that has just been created
     */
    protected void onCreateDatabase(SQLiteDatabase db) {
    }

    /**
     * Create (or get) the Cupboard instance this content provider is using
     *
     * @return the cupboard instance to use. Defaults to CupboardFactory.cupboard().
     */
    protected Cupboard createCupboard() {
        return cupboard();
    }

    @Override
    protected SQLiteOpenHelper getDatabaseHelper(Context context) {
        return new DatabaseHelper(context, mDatabaseName, mDatabaseVersion);
    }

    @Override
    protected Uri insertInTransaction(Uri uri, ContentValues values) {
        if (sUriHelper.matches(uri)) {
            Class<?> entityClass = sUriHelper.getMatchedClass(uri);
            SQLiteDatabase db = getDatabaseHelper().getWritableDatabase();
            sanitizeContentValues(uri, values);
            setExistingId(db, uri, entityClass, values);
            long id = mCupboard.withDatabase(db).put(entityClass, values);
            return sUriHelper.isCollection(uri) ? ContentUris.withAppendedId(uri, id) : uri;
        }
        throw new IllegalArgumentException("Unknown uri for insert: " + uri);
    }

    @Override
    protected int updateInTransaction(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (sUriHelper.matches(uri)) {
            if (sUriHelper.isCollection(uri)) {
                return mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).update(sUriHelper.getMatchedClass(uri), values, selection, selectionArgs);
            } else {
                return mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).update(sUriHelper.getMatchedClass(uri), values);
            }
        }
        throw new IllegalArgumentException("Unknown uri for update: " + uri);
    }

    @Override
    protected int deleteInTransaction(Uri uri, String selection, String[] selectionArgs) {
        if (sUriHelper.matches(uri)) {
            if (sUriHelper.isCollection(uri)) {
                return mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).delete(sUriHelper.getMatchedClass(uri), selection, selectionArgs);
            } else {
                if (mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).delete(sUriHelper.getMatchedClass(uri), ContentUris.parseId(uri))) {
                    return 1;
                }
                return 0;
            }
        }
        throw new IllegalArgumentException("Unknown uri for delete: " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (sUriHelper.matches(uri)) {
            if (sUriHelper.isCollection(uri)) {
                QueryBuilder<?> builder = mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).query(sUriHelper.getMatchedClass(uri)).
                        withProjection(projection).
                        withSelection(selection, selectionArgs).
                        orderBy(sortOrder);
                String limit = uri.getQueryParameter("limit");
                if (limit != null) {
                    builder.limit(Integer.parseInt(limit));
                }
                String distinct = uri.getQueryParameter("distinct");
                if ("true".equals(distinct)) {
                    builder.distinct();
                }
                builder.groupBy(uri.getQueryParameter("groupBy"));
                builder.having(uri.getQueryParameter("having"));
                return notifyingCursor(uri, builder.getCursor());
            } else {
                return notifyingCursor(uri, mCupboard.withDatabase(getDatabaseHelper().getWritableDatabase()).query(sUriHelper.getMatchedClass(uri)).byId(ContentUris.parseId(uri)).getCursor());
            }
        }

        throw new IllegalArgumentException("Unknown uri for query: " + uri);
    }

    private Cursor notifyingCursor(Uri uri, Cursor cursor) {
        if (cursor != null) {
            Class<?> clz = sUriHelper.getMatchedClass(uri);
            cursor.setNotificationUri(getContext().getContentResolver(), sUriHelper.getClassUri(clz));
        }
        return cursor;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info != null) {
            sUriHelper = new UriHelper(info.authority, mCupboard.getRegisteredEntities());
        }
    }

    @Override
    public boolean onCreate() {
        return super.onCreate();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    protected void notifyChange(boolean syncToNetwork) {
        getContext().getContentResolver().notifyChange(sUriHelper.getBaseUri(), null, syncToNetwork);
    }

    private void sanitizeContentValues(Uri uri, ContentValues values) {
        if (sUriHelper.matches(uri)) {
            if (sUriHelper.isCollection(uri)) {
                values.remove(BaseColumns._ID);
            } else {
                values.put(BaseColumns._ID, ContentUris.parseId(uri));
            }
        }
    }

    private void setExistingId(SQLiteDatabase db, Uri uri, Class<?> entityClass, ContentValues contentValues) {
        if (sUriHelper.isCollection(uri)) {
            setExistingId(db, entityClass, contentValues);
        }
    }

    /**
     * Set the existing id based on the entity class and content values. This is useful if the entity has an external key that isn't
     * the same as the entity _id field. This is not triggered for inserts on uri's that contain the id for obvious reasons.
     *
     * @param db            the database to match the id
     * @param entityClass   the entity class
     * @param contentValues the content values for the entity to be inserted.
     */
    protected void setExistingId(SQLiteDatabase db, Class<?> entityClass, ContentValues contentValues) {
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context, String name, int version) {
            super(context, name, null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            mCupboard.withDatabase(db).createTables();
            onCreateDatabase(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            mCupboard.withDatabase(db).upgradeTables();
            onUpgradeDatabase(db, oldVersion, newVersion);
        }
    }
}