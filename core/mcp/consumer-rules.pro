# Ktor Netty runs on Android's JDK SSL provider. Netty's native TLS, JFR/JMX and
# alternate logging integrations are optional and are not packaged by AzureQL.
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn jdk.jfr.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
