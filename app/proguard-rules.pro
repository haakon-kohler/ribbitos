# The Anthropic SDK ships R8 rules (META-INF/proguard/anthropic-java-core.pro), but under AGP 8's R8
# full mode they still let it strip members Jackson reaches by reflection: on the R1 a release build
# failed every Claude request with "JsonMissing cannot be serialized (... ["cache_control"])". Debug
# builds and the JVM tests never shrink, so only a device test of a release build catches this.
# Keep the SDK whole.
-keep class com.anthropic.** { *; }

# Kept that way, R8 also traces the SDK's structured-outputs JSON-schema generator (victools), which
# names two reflection types Android lacks. ribbitOS never generates schemas, so that code never runs.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
