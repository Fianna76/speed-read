# R8 rules for the release build. Libraries ship their own consumer rules; only app-specific
# reflection needs to be declared here.

# Navigation 3 saves the back stack by writing each key's class name and restores it with
# Class.forName(...) plus a reflective serializer lookup, so route classes must keep their
# names and generated serializer members.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# Keep source file/line information for readable crash stack traces (deobfuscate with the
# mapping file in app/build/outputs/mapping/release/), but hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
