package dev.xpramt.redmagicvowifi;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * App side of the settings hand-over. The app's own file is the source of truth; the framework only holds a
 * copy (remote preferences) that hooked processes can read at once, even before the app runs. Whenever the
 * framework service connects (first start, framework restarted or replaced) and whenever a setting changes, the
 * copy is made equal to the app's values.
 */
final class FrameworkSync {
    private static final String TAG = "RedMagicX";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RedMagicX-sync");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean started;
    private static XposedService service;
    private static Map<String, Object> latest;

    private FrameworkSync() {
    }

    /** Registers for the framework service once per process. {@code initial} are the app's current values. */
    static synchronized void start(Map<String, ?> initial) {
        latest = new HashMap<>(initial);
        if (started) {
            return;
        }
        started = true;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService bound) {
                synchronized (FrameworkSync.class) {
                    service = bound;
                }
                pushLater();
            }

            @Override
            public void onServiceDied(XposedService died) {
                synchronized (FrameworkSync.class) {
                    if (service == died) {
                        service = null;
                    }
                }
            }
        });
    }

    /** The app's values changed: publish them. */
    static synchronized void publish(Map<String, ?> values) {
        latest = new HashMap<>(values);
        pushLater();
    }

    /** Experiment: asks the framework to hot-reload the module in the hooked process named {@code process}. */
    static String hotReload(String process) {
        XposedService bound;
        synchronized (FrameworkSync.class) {
            bound = service;
        }
        if (bound == null) {
            return "no framework service";
        }
        try {
            if (bound.getApiVersion() < XposedService.API_102) {
                return "framework service api " + bound.getApiVersion() + " < 102";
            }
            StringBuilder seen = new StringBuilder();
            for (io.github.libxposed.service.HookedTarget target : bound.getRunningTargets()) {
                seen.append(target.getProcessName()).append('#').append(target.getPid())
                        .append(':').append(target.getState()).append(' ');
                if (process.equals(target.getProcessName())) {
                    bound.hotReloadModule(target, null, (done, result) -> Log.i(TAG, "hot reload "
                            + done.getProcessName() + " -> " + result.status() + " " + result.message()));
                    return "requested for " + process + "; targets: " + seen;
                }
            }
            return "no running target named " + process + "; targets: " + seen;
        } catch (RuntimeException exception) {
            return "failed: " + exception;
        }
    }

    private static void pushLater() {
        EXECUTOR.execute(FrameworkSync::push);
    }

    private static void push() {
        XposedService bound;
        Map<String, Object> values;
        synchronized (FrameworkSync.class) {
            bound = service;
            values = latest;
        }
        if (bound == null || values == null) {
            return;
        }
        try {
            if ((bound.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                Log.w(TAG, "framework offers no remote preferences; hooks will ask the app directly");
                return;
            }
            SharedPreferences remote = bound.getRemotePreferences(Config.REMOTE_GROUP);
            if (remote.getAll().equals(values)) {
                return;
            }
            SharedPreferences.Editor editor = remote.edit();
            editor.clear();
            for (Map.Entry<String, Object> item : values.entrySet()) {
                Object value = item.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(item.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(item.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(item.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(item.getKey(), (Float) value);
                } else if (value instanceof String) {
                    editor.putString(item.getKey(), (String) value);
                }
            }
            boolean ok = editor.commit();
            Log.i(TAG, "published " + values.size() + " settings to the framework: " + ok);
        } catch (RuntimeException exception) {
            Log.w(TAG, "publishing settings to the framework failed", exception);
        }
    }
}
