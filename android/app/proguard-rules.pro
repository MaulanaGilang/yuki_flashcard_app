# kotlinx.serialization: keep generated serializers for @Serializable classes (nav keys, manifests).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class app.yuki.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.yuki.**$$serializer { *; }
