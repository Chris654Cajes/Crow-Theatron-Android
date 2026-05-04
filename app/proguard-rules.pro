# Crow Théatron ProGuard rules

# Media3 / ExoPlayer — keep all player internals
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep data layer intact (SQLite + entities)
-keep class com.crowtheatron.app.data.** { *; }

# Keep enums (used in DB storage by storageKey)
-keepclassmembers enum com.crowtheatron.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public ** storageKey;
    public ** displayName;
}

# Keep ViewBinding generated classes
-keep class com.crowtheatron.app.databinding.** { *; }

# Service must survive minification
-keep class com.crowtheatron.app.service.PlaybackService { *; }

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
