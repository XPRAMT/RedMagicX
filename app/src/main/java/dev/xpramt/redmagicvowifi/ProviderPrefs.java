package dev.xpramt.redmagicvowifi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hook-side reader that asks the app itself for the settings, through its content provider.
 *
 * It is used when the framework's own copy is missing (the framework was replaced or does not
 * support the legacy shared-preferences redirect), so the hooks get the app's values, which are the
 * source of truth, whatever the framework does. The last answer is cached; a refresh never blocks
 * a hooked process once it has a value. In {@code system_server} nothing here ever blocks.
 */
final class ProviderPrefs {
    private static final long REFRESH_AFTER_MS = 3000L;
    private static final long RETRY_AFTER_MS = 3000L;
    private static final long FIRST_FETCH_WAIT_MS = 1500L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RedMagicX-config");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static volatile Map<String, Object> cache;
    private static volatile long fetchedAt;
    private static volatile long lastAttempt;

    private ProviderPrefs() {
    }

    /** The app's settings, or null when the app has not answered yet. */
    static SharedPreferences get() {
        long now = SystemClock.elapsedRealtime();
        Map<String, Object> data = cache;
        if (data == null) {
            if (now - lastAttempt >= RETRY_AFTER_MS) {
                if (isSystemServer()) {
                    refreshAsync();
                } else {
                    fetchAndWait();
                }
            }
            data = cache;
        } else if (now - fetchedAt >= REFRESH_AFTER_MS) {
            refreshAsync();
        }
        return data == null ? null : new MapPrefs(data);
    }

    /** Hot reload: the old generation stops its background thread. */
    static void shutdown() {
        EXECUTOR.shutdown();
    }

    /** No settings at all: every default. */
    static SharedPreferences empty() {
        return new MapPrefs(new HashMap<>());
    }

    private static boolean isSystemServer() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return "android".equals(thread.getMethod("currentPackageName").invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void refreshAsync() {
        if (FETCHING.compareAndSet(false, true)) {
            lastAttempt = SystemClock.elapsedRealtime();
            EXECUTOR.execute(ProviderPrefs::fetch);
        }
    }

    private static void fetchAndWait() {
        if (!FETCHING.compareAndSet(false, true)) {
            return;
        }
        lastAttempt = SystemClock.elapsedRealtime();
        Future<?> job = EXECUTOR.submit(ProviderPrefs::fetch);
        try {
            job.get(FIRST_FETCH_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // The fetch keeps running; the next call picks the result up from the cache.
        }
    }

    private static void fetch() {
        try {
            Context context = context();
            if (context == null) {
                return;
            }
            Bundle answer = context.getContentResolver().call(
                    Uri.parse("content://" + ThemeConfigProvider.AUTHORITY),
                    ThemeConfigProvider.METHOD_GET_ALL, null, null);
            if (answer == null || answer.isEmpty()) {
                return;
            }
            Map<String, Object> values = new HashMap<>();
            for (String key : answer.keySet()) {
                @SuppressWarnings("deprecation")
                Object value = answer.get(key);
                if (value != null) {
                    values.put(key, value);
                }
            }
            cache = values;
            fetchedAt = SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
            // The app is not installed, not reachable yet (early boot), or the call failed.
        } finally {
            FETCHING.set(false);
        }
    }

    /** The system context exists as soon as the process has an ActivityThread, before the app does. */
    private static Context context() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object current = thread.getMethod("currentActivityThread").invoke(null);
            if (current != null) {
                Object system = thread.getMethod("getSystemContext").invoke(current);
                if (system instanceof Context) {
                    return (Context) system;
                }
            }
            Object application = thread.getMethod("currentApplication").invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A read-only view of one answer. */
    private static final class MapPrefs implements SharedPreferences {
        private final Map<String, Object> values;

        MapPrefs(Map<String, Object> values) {
            this.values = values;
        }

        @Override public Map<String, ?> getAll() {
            return values;
        }

        @Override public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override public long getLong(String key, long defValue) {
            Object value = values.get(key);
            if (value instanceof Long) {
                return (Long) value;
            }
            return value instanceof Integer ? ((Integer) value).longValue() : defValue;
        }

        @Override public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override public Editor edit() {
            throw new UnsupportedOperationException("read-only");
        }

        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        }

        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        }
    }
}
