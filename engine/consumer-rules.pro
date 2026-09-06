# JNI entry points are looked up by name, so R8 must not rename them.
-keepclasseswithmembernames,includedescriptorclasses class dev.mariobox.engine.** {
    native <methods>;
}
-keep class dev.mariobox.engine.MbEngine { *; }

# The host calls back with GetMethodID(cls, "onEngineEvent", "(ILjava/lang/String;)V").
# That string is hardcoded in C++, so the interface's method name has to survive
# minification; R8 renames an interface method and all of its overrides together,
# which would silently leave every engine event undelivered in release builds only.
-keep interface dev.mariobox.engine.EngineListener { *; }
