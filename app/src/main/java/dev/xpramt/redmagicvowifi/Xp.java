package dev.xpramt.redmagicvowifi;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedModule;

/**
 * Thin helpers over the libxposed API (102): before/after style hooks on top of {@code hook().intercept()}, and the
 * reflection helpers the hooks need (the modern API ships none). Callbacks never throw into the hooked process:
 * an exception in a callback is logged and the original call goes on.
 */
final class Xp {
    private static final String TAG = "RedMagicX";

    /** True while a new generation reinstalls its hooks after a hot reload (not a process start). */
    static volatile boolean hotReloading;

    private static volatile XposedModule module;
    private static final Map<Object, Map<String, Object>> EXTRAS = Collections.synchronizedMap(new WeakHashMap<>());

    private Xp() {
    }

    static void init(XposedModule instance) {
        module = instance;
    }

    static XposedModule module() {
        return module;
    }

    static void log(String message) {
        String line = "[" + Config.BUILD_MARKER + "] " + message;
        XposedModule current = module;
        if (current != null) {
            current.log(Log.INFO, TAG, line);
        } else {
            Log.i(TAG, line);
        }
    }

    /** The package being hooked: its name, class loader and application info. */
    static final class Target {
        final String packageName;
        final ClassLoader classLoader;
        final ApplicationInfo appInfo;

        Target(String packageName, ClassLoader classLoader, ApplicationInfo appInfo) {
            this.packageName = packageName;
            this.classLoader = classLoader;
            this.appInfo = appInfo;
        }
    }

    /** One call of a hooked method. Arguments may be replaced in place; a result set in before skips the original. */
    static final class Param {
        final Object thisObject;
        final Object[] args;
        final Member method;
        private Object result;
        private Throwable throwable;
        private boolean done;

        Param(Object thisObject, Object[] args, Member method) {
            this.thisObject = thisObject;
            this.args = args;
            this.method = method;
        }

        Object getResult() {
            return result;
        }

        Throwable getThrowable() {
            return throwable;
        }

        void setResult(Object value) {
            result = value;
            throwable = null;
            done = true;
        }

        void setThrowable(Throwable value) {
            throwable = value;
            result = null;
            done = true;
        }
    }

    abstract static class MethodHook {
        final int priority;

        MethodHook() {
            this(XposedInterface.PRIORITY_DEFAULT);
        }

        MethodHook(int priority) {
            this.priority = priority;
        }

        protected void before(Param param) throws Throwable {
        }

        protected void after(Param param) throws Throwable {
        }
    }

    /** A hook that replaces the method with a constant result. */
    static MethodHook returnConstant(Object value) {
        return new MethodHook() {
            @Override
            protected void before(Param param) {
                param.setResult(value);
            }
        };
    }

    static HookHandle hookMethod(Member member, MethodHook callback) {
        XposedModule current = module;
        if (current == null) {
            throw new IllegalStateException("module not loaded");
        }
        if (!(member instanceof Executable)) {
            throw new IllegalArgumentException("not an executable: " + member);
        }
        Executable executable = (Executable) member;
        return current.hook(executable)
                .setPriority(callback.priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(chain -> run(chain, callback));
    }

    private static Object run(XposedInterface.Chain chain, MethodHook callback) throws Throwable {
        Param param = new Param(chain.getThisObject(), chain.getArgs().toArray(), chain.getExecutable());
        try {
            callback.before(param);
        } catch (Throwable t) {
            log("hook before failed in " + chain.getExecutable().getName() + ": " + Log.getStackTraceString(t));
            param.result = null;
            param.throwable = null;
            param.done = false;
        }
        if (!param.done) {
            try {
                param.result = chain.proceed(param.args);
            } catch (Throwable t) {
                param.throwable = t;
            }
        }
        try {
            callback.after(param);
        } catch (Throwable t) {
            log("hook after failed in " + chain.getExecutable().getName() + ": " + Log.getStackTraceString(t));
        }
        if (param.throwable != null) {
            throw param.throwable;
        }
        return param.result;
    }

    static Set<HookHandle> hookAllMethods(Class<?> clazz, String name, MethodHook callback) {
        Set<HookHandle> handles = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                handles.add(hookMethod(method, callback));
            }
        }
        if (handles.isEmpty()) {
            throw new NoSuchMethodError(clazz.getName() + "#" + name);
        }
        return handles;
    }

    static HookHandle findAndHookMethod(Class<?> clazz, String name, Object... typesThenHook) {
        MethodHook callback = lastAsHook(typesThenHook);
        Method method = findMethodExact(clazz, name, resolveTypes(typesThenHook, clazz.getClassLoader()));
        return hookMethod(method, callback);
    }

    static HookHandle findAndHookMethod(String className, ClassLoader loader, String name, Object... typesThenHook) {
        return findAndHookMethod(findClass(className, loader), name, typesThenHook);
    }

    private static MethodHook lastAsHook(Object[] typesThenHook) {
        if (typesThenHook.length == 0 || !(typesThenHook[typesThenHook.length - 1] instanceof MethodHook)) {
            throw new IllegalArgumentException("last argument must be a MethodHook");
        }
        return (MethodHook) typesThenHook[typesThenHook.length - 1];
    }

    private static Class<?>[] resolveTypes(Object[] typesThenHook, ClassLoader loader) {
        Class<?>[] types = new Class<?>[typesThenHook.length - 1];
        for (int i = 0; i < types.length; i++) {
            Object item = typesThenHook[i];
            if (item instanceof Class) {
                types[i] = (Class<?>) item;
            } else if (item instanceof String) {
                types[i] = findClass((String) item, loader);
            } else {
                throw new IllegalArgumentException("parameter type #" + i + " is neither Class nor String");
            }
        }
        return types;
    }

    static Method findMethodExact(Class<?> clazz, String name, Class<?>... types) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + name);
    }

    // ---- classes and fields ----

    static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(name);
        }
    }

    static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + name);
    }

    static Object getObjectField(Object object, String name) {
        try {
            return findField(object.getClass(), name).get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setObjectField(Object object, String name, Object value) {
        try {
            findField(object.getClass(), name).set(object, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static int getIntField(Object object, String name) {
        try {
            return findField(object.getClass(), name).getInt(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static long getLongField(Object object, String name) {
        try {
            return findField(object.getClass(), name).getLong(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setLongField(Object object, String name, long value) {
        try {
            findField(object.getClass(), name).setLong(object, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static int getStaticIntField(Class<?> clazz, String name) {
        try {
            return findField(clazz, name).getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- calls ----

    static Object callMethod(Object object, String name, Object... args) {
        return invoke(object.getClass(), object, name, args);
    }

    static Object callStaticMethod(Class<?> clazz, String name, Object... args) {
        return invoke(clazz, null, name, args);
    }

    private static Object invoke(Class<?> clazz, Object target, String name, Object[] args) {
        Method method = findMethodForArgs(clazz, name, args);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(name + " threw " + cause, cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findMethodForArgs(Class<?> clazz, String name, Object[] args) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        for (Class<?> iface : allInterfaces(clazz)) {
            for (Method method : iface.getDeclaredMethods()) {
                if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)
                        && !Modifier.isAbstract(method.getModifiers())) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + name + " with " + args.length + " args");
    }

    private static List<Class<?>> allInterfaces(Class<?> clazz) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                result.add(iface);
                result.addAll(allInterfaces(iface));
            }
        }
        return result;
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
            } else if (!box(types[i]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    // ---- extra per-object state ----

    static Object getAdditionalInstanceField(Object object, String key) {
        Map<String, Object> values = EXTRAS.get(object);
        return values == null ? null : values.get(key);
    }

    static void setAdditionalInstanceField(Object object, String key, Object value) {
        synchronized (EXTRAS) {
            Map<String, Object> values = EXTRAS.get(object);
            if (values == null) {
                values = new HashMap<>();
                EXTRAS.put(object, values);
            }
            values.put(key, value);
        }
    }
}
