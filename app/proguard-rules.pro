-keep class pt.ismai.farol.data.remote.dto.** { *; }
-keep class pt.ismai.farol.domain.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# WorkManager keeps its jobs in a Room database whose generated class is created by reflection. R8
# removes the unused-looking constructor, and the release app then crashes on start-up.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
