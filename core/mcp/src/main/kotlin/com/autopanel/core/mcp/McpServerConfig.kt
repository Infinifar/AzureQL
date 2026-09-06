package com.autopanel.core.mcp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class McpNetworkAccess {
    LOOPBACK_ONLY,
    LOCAL_NETWORK
}

data class McpServerSettings(
    val networkAccess: McpNetworkAccess = McpNetworkAccess.LOOPBACK_ONLY
) {
    fun toServerConfig(port: Int = McpServerConfig.DEFAULT_PORT) = McpServerConfig(
        port = port,
        networkAccess = networkAccess
    )
}

interface McpServerSettingsStore {
    val settings: StateFlow<McpServerSettings>
    suspend fun setNetworkAccess(networkAccess: McpNetworkAccess)
}

@Singleton
class AndroidMcpServerSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : McpServerSettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<McpServerSettings> = mutableSettings.asStateFlow()

    override suspend fun setNetworkAccess(networkAccess: McpNetworkAccess) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(preferences.edit().putString(KEY_NETWORK_ACCESS, networkAccess.name).commit()) {
                "Unable to persist MCP network access"
            }
            mutableSettings.value = McpServerSettings(networkAccess)
        }
    }

    private fun loadSettings(): McpServerSettings {
        val stored = preferences.getString(KEY_NETWORK_ACCESS, null)
        val access = stored?.let { value ->
            runCatching { McpNetworkAccess.valueOf(value) }.getOrNull()
        } ?: McpNetworkAccess.LOOPBACK_ONLY
        return McpServerSettings(access)
    }

    private companion object {
        const val PREFERENCES_NAME = "azureql_mcp_server"
        const val KEY_NETWORK_ACCESS = "network_access_v1"
    }
}

data class McpServerConfig(
    val port: Int = DEFAULT_PORT,
    val networkAccess: McpNetworkAccess = McpNetworkAccess.LOOPBACK_ONLY
) {
    init {
        require(port in 1024..65535) { "MCP port must be between 1024 and 65535" }
    }

    val bindAddress: String
        get() = when (networkAccess) {
            McpNetworkAccess.LOOPBACK_ONLY -> LOOPBACK_ADDRESS
            McpNetworkAccess.LOCAL_NETWORK -> ALL_INTERFACES_ADDRESS
        }

    val endpoint: String
        get() = endpointFor(LOOPBACK_ADDRESS)

    fun endpointFor(host: String): String {
        val formattedHost = if (':' in host && !host.startsWith('[')) "[$host]" else host
        return "http://$formattedHost:$port/mcp"
    }

    companion object {
        const val DEFAULT_PORT = 18765
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val ALL_INTERFACES_ADDRESS = "0.0.0.0"
    }
}

internal fun McpServerConfig.allowedHostAddresses(): Set<String> {
    val loopback = linkedSetOf(McpServerConfig.LOOPBACK_ADDRESS, "localhost", "::1")
    if (networkAccess == McpNetworkAccess.LOOPBACK_ONLY) return loopback

    val interfaceAddresses = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.name.isCellularInterfaceName() }
            .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
            .filter { address ->
                !address.isAnyLocalAddress &&
                    !address.isMulticastAddress &&
                    (address is Inet4Address || address is Inet6Address) &&
                    (
                        address.isLoopbackAddress ||
                            address.isSiteLocalAddress ||
                            address is Inet6Address && address.isUniqueLocalAddress()
                    )
            }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())
    }.getOrDefault(emptySet())
    return loopback + interfaceAddresses
}

internal fun McpServerConfig.accessibleEndpoints(allowedHosts: Set<String>): List<String> {
    val hosts = if (networkAccess == McpNetworkAccess.LOOPBACK_ONLY) {
        listOf(McpServerConfig.LOOPBACK_ADDRESS)
    } else {
        allowedHosts
            .filterNot { it == "localhost" || it == McpServerConfig.LOOPBACK_ADDRESS || it == "::1" }
            .sortedBy { ':' in it }
            .take(MAX_DISPLAYED_ENDPOINTS)
    }
    return hosts.map(::endpointFor).distinct()
}

private fun Inet6Address.isUniqueLocalAddress(): Boolean =
    address.firstOrNull()?.toInt()?.and(0xfe) == 0xfc

private fun String.isCellularInterfaceName(): Boolean {
    val normalized = lowercase()
    return CELLULAR_INTERFACE_PREFIXES.any(normalized::startsWith)
}

private val CELLULAR_INTERFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "wwan")
private const val MAX_DISPLAYED_ENDPOINTS = 5
