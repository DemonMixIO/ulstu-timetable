# Keep jsoup reflective bits safe (release builds are currently unminified anyway).
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# Models are serialized by hand via org.json; keep them if minification is enabled later.
-keep class ru.ulstu.timetable.model.** { *; }

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }
