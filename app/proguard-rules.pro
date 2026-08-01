# Keep Kotlin reflection
-keep class kotlin.reflect.** { *; }

# Keep collectors
-keep class com.bon.gamemonitor.collector.** { *; }

# Keep engine
-keep class com.bon.gamemonitor.engine.** { *; }

# Keep view binding
-keep class androidx.viewbinding.** { *; }

# Keep coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
