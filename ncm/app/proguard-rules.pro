# Miuix KMP — Compose 库，无需额外规则，Compose 编译器插件自动处理

# kotlinx 不可变集合
-keep class kotlinx.collections.immutable.** { *; }

# 保留 Application 和 Activity
-keep class com.mcn.fix.** { *; }

# 移除无用日志（不影响功能）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
