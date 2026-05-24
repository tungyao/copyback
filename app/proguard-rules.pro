# Add project specific ProGuard rules here.
# SMBJ 使用 BouncyCastle，需要保留相关类
-dontwarn com.hierynomus.**
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.slf4j.** { *; }
-keep class net.engio.mbassy.** { *; }

# 保留数据模型（反射）
-keep class com.copyback.data.model.** { *; }
