# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# SQLCipher specific rules
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.sqlcipher.SupportOpenHelperFactory { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Bouncy Castle (for ML-KEM and hybrid PQ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Google Tink crypto
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Standard Android lifecycle keeping rules
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
