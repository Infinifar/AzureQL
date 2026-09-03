# Ktor Netty runs on Android's JDK SSL provider. Netty's native TLS, JFR/JMX and
# alternate logging integrations are optional and are not packaged by AzureQL.
-dontwarn io.netty.internal.tcnative.**
# Netty's transport/bootstrap and buffer-leak helpers use reflection and method
# handles (including the server-channel constructor and toLeakAwareBuffer).
# Keeping only the channel constructor still crashes a minified Android build.
-keep class io.netty.** { *; }
# Brotli is an optional Netty codec and is not packaged or used by the local
# MCP server. Keeping Netty exposes its optional reference to R8.
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.**
-dontwarn io.netty.pkitesting.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.osgi.annotation.bundle.Export
-dontwarn reactor.blockhound.BlockHound$Builder
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn jdk.jfr.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
