package dev.xpramt.redmagicvowifi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class ThemeConfigProvider extends ContentProvider {
    static final String AUTHORITY = Config.PACKAGE_NAME + ".themeconfig";
    static final String METHOD_SET_SELECTION = "set_selection";
    /** Every setting, with its type, for hooked processes whose framework copy is missing. */
    static final String METHOD_GET_ALL = "get_all";

    @Override
    public boolean onCreate() {
        // Starts the settings hand-over to the framework as soon as the app process exists (a hooked
        // process asking for the settings starts it).
        if (getContext() != null) {
            Config.appPrefs(getContext());
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_GET_ALL.equals(method) && getContext() != null) {
            Bundle all = new Bundle();
            for (java.util.Map.Entry<String, ?> item : Config.appPrefs(getContext()).getAll().entrySet()) {
                Object value = item.getValue();
                if (value instanceof Boolean) {
                    all.putBoolean(item.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    all.putInt(item.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    all.putLong(item.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    all.putFloat(item.getKey(), (Float) value);
                } else if (value instanceof String) {
                    all.putString(item.getKey(), (String) value);
                }
            }
            return all;
        }
        if (!METHOD_SET_SELECTION.equals(method) || extras == null || getContext() == null) {
            return Bundle.EMPTY;
        }
        SharedPreferences.Editor editor = Config.appPrefs(getContext()).edit();
        editor.putBoolean(Config.KEY_THEME_FULL, extras.getBoolean(Config.KEY_THEME_FULL, false));
        editor.putBoolean(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS,
                extras.getBoolean(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS, false));
        editor.putBoolean(Config.KEY_THEME_LOCKSCREEN,
                extras.getBoolean(Config.KEY_THEME_LOCKSCREEN, false));
        editor.putBoolean(Config.KEY_THEME_TYPE_1, extras.getBoolean(Config.KEY_THEME_TYPE_1, true));
        editor.putBoolean(Config.KEY_THEME_TYPE_2, extras.getBoolean(Config.KEY_THEME_TYPE_2, true));
        editor.putBoolean(Config.KEY_THEME_TYPE_4, extras.getBoolean(Config.KEY_THEME_TYPE_4, false));
        editor.putBoolean(Config.KEY_THEME_TYPE_6, extras.getBoolean(Config.KEY_THEME_TYPE_6, true));
        editor.putBoolean(Config.KEY_THEME_TYPE_16, extras.getBoolean(Config.KEY_THEME_TYPE_16, false));
        editor.putBoolean(Config.KEY_THEME_TYPE_17, extras.getBoolean(Config.KEY_THEME_TYPE_17, true));
        editor.putBoolean(Config.KEY_THEME_TYPE_23, extras.getBoolean(Config.KEY_THEME_TYPE_23, true));
        Bundle result = new Bundle();
        result.putBoolean("saved", editor.commit());
        return result;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
