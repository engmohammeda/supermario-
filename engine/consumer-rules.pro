// JNI entry points are looked up by name, so R8 must not rename them.
-keepclasseswithmembernames,includedescriptorclasses class dev.mariobox.engine.** {
    native <methods>;
}
-keep class dev.mariobox.engine.MbEngine { *; }
-keep interface dev.mariobox.engine.MbNative$EngineListener { *; }
