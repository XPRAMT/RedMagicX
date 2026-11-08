package dev.xpramt.redmagicvowifi;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The app's own copy of the settings, and the source of truth for them.
 *
 * It is a JSON file under {@code Context.getFilesDir()}, not a {@code shared_prefs} file: with the
 * legacy "New XSharedPreferences" feature the framework redirects the whole {@code shared_prefs}
 * folder into its own random directory, which is lost when the framework is replaced. Nothing a
 * framework does reaches {@code filesDir}.
 *
 * Values keep their type (boolean, int, long, float, string). Every committed change is written
 * atomically and then reported to {@link #onChanged} so the framework copy can follow.
 */
final class FilePrefs implements SharedPreferences {
    interface OnChanged {
        void changed(Map<String, ?> snapshot);
    }

    private final File file;
    private final OnChanged onChanged;
    private final Object lock = new Object();
    private final Map<String, Object> values = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();

    FilePrefs(File file, OnChanged onChanged) {
        this.file = file;
        this.onChanged = onChanged;
        load();
    }

    /**
     * Reads a classic {@code shared_prefs/<name>.xml} straight from the file. The SharedPreferences
     * API cannot be used for this: while a framework redirects the preferences folder, even a
     * private {@code getSharedPreferences} call is served from the framework's folder.
     */
    static Map<String, Object> readLegacyXml(File xml) {
        Map<String, Object> result = new HashMap<>();
        if (xml == null || !xml.isFile()) {
            return result;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(xml)) {
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            int event = parser.getEventType();
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    String name = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");
                    if (name != null) {
                        switch (tag) {
                            case "boolean": result.put(name, Boolean.parseBoolean(value)); break;
                            case "int": result.put(name, Integer.parseInt(value)); break;
                            case "long": result.put(name, Long.parseLong(value)); break;
                            case "float": result.put(name, Float.parseFloat(value)); break;
                            case "string": {
                                String text = value != null ? value : parser.nextText();
                                result.put(name, text == null ? "" : text);
                                break;
                            }
                            default: break;
                        }
                    }
                }
                event = parser.next();
            }
        } catch (Exception exception) {
            result.clear();
        }
        return result;
    }

    boolean existsOnDisk() {
        return file.isFile() && file.length() > 0;
    }

    private void load() {
        synchronized (lock) {
            values.clear();
            if (!file.isFile()) {
                return;
            }
            try {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(text);
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject entry = root.getJSONObject(key);
                    Object value = decode(entry.getString("t"), entry);
                    if (value != null) {
                        values.put(key, value);
                    }
                }
            } catch (IOException | JSONException | RuntimeException exception) {
                // A damaged file is treated as empty; the next commit rewrites it.
                values.clear();
            }
        }
    }

    private static Object decode(String type, JSONObject entry) throws JSONException {
        switch (type) {
            case "b": return entry.getBoolean("v");
            case "i": return entry.getInt("v");
            case "l": return entry.getLong("v");
            case "f": return (float) entry.getDouble("v");
            case "s": return entry.getString("v");
            default: return null;
        }
    }

    private static JSONObject encode(Object value) throws JSONException {
        JSONObject entry = new JSONObject();
        if (value instanceof Boolean) {
            entry.put("t", "b").put("v", ((Boolean) value).booleanValue());
        } else if (value instanceof Integer) {
            entry.put("t", "i").put("v", ((Integer) value).intValue());
        } else if (value instanceof Long) {
            entry.put("t", "l").put("v", ((Long) value).longValue());
        } else if (value instanceof Float) {
            entry.put("t", "f").put("v", ((Float) value).doubleValue());
        } else {
            entry.put("t", "s").put("v", String.valueOf(value));
        }
        return entry;
    }

    /** Writes the file next to itself first and renames it over the old one. */
    private boolean persist(Map<String, Object> snapshot) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Object> item : snapshot.entrySet()) {
                root.put(item.getKey(), encode(item.getValue()));
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return false;
            }
            File temporary = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            return temporary.renameTo(file);
        } catch (IOException | JSONException exception) {
            return false;
        }
    }

    Map<String, Object> copyOfAll() {
        synchronized (lock) {
            return new HashMap<>(values);
        }
    }

    /** Replaces everything with {@code source}, used once to import an older copy of the settings. */
    boolean replaceAll(Map<String, ?> source) {
        Map<String, Object> next = new HashMap<>();
        for (Map.Entry<String, ?> item : source.entrySet()) {
            Object value = item.getValue();
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof String) {
                next.put(item.getKey(), value);
            }
        }
        synchronized (lock) {
            if (!persist(next)) {
                return false;
            }
            values.clear();
            values.putAll(next);
        }
        return true;
    }

    @Override public Map<String, ?> getAll() {
        return copyOfAll();
    }

    @Override public String getString(String key, String defValue) {
        synchronized (lock) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }
    }

    @Override public Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    @Override public int getInt(String key, int defValue) {
        synchronized (lock) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }
    }

    @Override public long getLong(String key, long defValue) {
        synchronized (lock) {
            Object value = values.get(key);
            if (value instanceof Long) {
                return (Long) value;
            }
            return value instanceof Integer ? ((Integer) value).longValue() : defValue;
        }
    }

    @Override public float getFloat(String key, float defValue) {
        synchronized (lock) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }
    }

    @Override public boolean getBoolean(String key, boolean defValue) {
        synchronized (lock) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }
    }

    @Override public boolean contains(String key) {
        synchronized (lock) {
            return values.containsKey(key);
        }
    }

    @Override public Editor edit() {
        return new EditorImpl();
    }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        synchronized (lock) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        synchronized (lock) {
            listeners.remove(listener);
        }
    }

    private final class EditorImpl implements Editor {
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clear;

        @Override public Editor putString(String key, String value) {
            return value == null ? remove(key) : stage(key, value);
        }

        @Override public Editor putStringSet(String key, Set<String> values) {
            return this;
        }

        @Override public Editor putInt(String key, int value) {
            return stage(key, value);
        }

        @Override public Editor putLong(String key, long value) {
            return stage(key, value);
        }

        @Override public Editor putFloat(String key, float value) {
            return stage(key, value);
        }

        @Override public Editor putBoolean(String key, boolean value) {
            return stage(key, value);
        }

        private Editor stage(String key, Object value) {
            pending.put(key, value);
            removed.remove(key);
            return this;
        }

        @Override public Editor remove(String key) {
            pending.remove(key);
            removed.add(key);
            return this;
        }

        @Override public Editor clear() {
            clear = true;
            return this;
        }

        @Override public boolean commit() {
            Map<String, Object> next;
            Set<String> touched = new HashSet<>();
            List<OnSharedPreferenceChangeListener> notify;
            synchronized (lock) {
                next = new HashMap<>(values);
                if (clear) {
                    touched.addAll(next.keySet());
                    next.clear();
                }
                for (String key : removed) {
                    if (next.remove(key) != null) {
                        touched.add(key);
                    }
                }
                for (Map.Entry<String, Object> item : pending.entrySet()) {
                    Object before = next.put(item.getKey(), item.getValue());
                    if (before == null || !before.equals(item.getValue())) {
                        touched.add(item.getKey());
                    }
                }
                if (touched.isEmpty()) {
                    return true;
                }
                if (!persist(next)) {
                    return false;
                }
                values.clear();
                values.putAll(next);
                notify = new ArrayList<>(listeners);
            }
            if (onChanged != null) {
                onChanged.changed(Collections.unmodifiableMap(next));
            }
            for (OnSharedPreferenceChangeListener listener : notify) {
                for (String key : touched) {
                    listener.onSharedPreferenceChanged(FilePrefs.this, key);
                }
            }
            return true;
        }

        @Override public void apply() {
            commit();
        }
    }
}
