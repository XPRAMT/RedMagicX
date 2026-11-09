# The framework instantiates the module entry from META-INF/xposed/java_init.list by name.
-keep class dev.xpramt.redmagicvowifi.HookEntry { *; }
# libxposed's API classes are provided by the framework at run time.
-dontwarn io.github.libxposed.api.**
# Shizuku.newProcess is called through reflection (MainActivity).
-keep class rikka.shizuku.** { *; }
# The framework service talks to the module over AIDL.
-keep class io.github.libxposed.service.** { *; }
