# MX SpatialBridge ProGuard rules

# Keep WebSocket classes (Java-WebSocket library)
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# Keep Gson model classes (profile schema)
-keep class com.logitech.mxspatialbridge.mapper.InputMapperEngine$SpatialProfile { *; }
-keep class com.logitech.mxspatialbridge.mapper.InputMapperEngine$ActionDescriptor { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep HID service classes
-keep class com.logitech.mxspatialbridge.hid.** { *; }
-keep class com.logitech.mxspatialbridge.sync.** { *; }
