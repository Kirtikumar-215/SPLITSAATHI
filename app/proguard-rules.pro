# SplitSaathi Production ProGuard / R8 Rules

# Strip sensitive logging in release builds (Requirement 6: no logging of money or personal data)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve Room Database and entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep SplitSaathi data models
-keep class com.example.data.model.** { *; }
