# Nothing here uses reflection; the default optimize rules are enough.
-keep class kotlin.Metadata { *; }

# Health Connect client
-keep class androidx.health.connect.client.** { *; }
-dontwarn androidx.health.**
