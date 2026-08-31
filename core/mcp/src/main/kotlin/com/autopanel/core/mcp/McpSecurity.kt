package com.autopanel.core.mcp

import android.content.Context
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@JvmInline
value class McpAgentId(val value: String)

enum class McpScope {
    STATUS_READ,
    TASK_READ,
    SCRIPT_READ,
    DEPENDENCY_READ,
    ENV_READ_METADATA,
    LOG_READ,
    TASK_WRITE,
    SCRIPT_WRITE,
    DEPENDENCY_WRITE,
    ENV_WRITE,
    TASK_EXECUTE
}

enum class McpRiskLevel { LOW_READ, SENSITIVE_READ, CONTROLLED_WRITE, EXECUTION, HIGH_RISK }

data class McpAgent(
    val id: McpAgentId,
    val name: String,
    val scopes: Set<McpScope>,
    val allowedAccountIds: Set<String>,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long? = null
)

data class McpIssuedCredential(val agent: McpAgent, val token: String)

interface McpAgentStore {
    val agents: StateFlow<List<McpAgent>>
    suspend fun issue(name: String, scopes: Set<McpScope>, accountIds: Set<String>): McpIssuedCredential
    suspend fun authenticate(token: String): McpAgent?
    suspend fun rename(agentId: McpAgentId, name: String): McpAgent
    suspend fun revoke(agentId: McpAgentId)
}

@Singleton
class McpAgentManager @Inject constructor(
    private val store: McpAgentStore,
    private val accountIdentityProvider: ActiveAccountIdentityProvider
) {
    val agents: StateFlow<List<McpAgent>> = store.agents

    suspend fun issueReadOnlyAgent(name: String): Result<McpIssuedCredential> = runCatching {
        val account = checkNotNull(accountIdentityProvider.current()) {
            "No authenticated QingLong account is selected"
        }
        store.issue(
            name = name.trim().takeIf(String::isNotEmpty) ?: "Local AI agent",
            scopes = DEFAULT_READ_SCOPES,
            accountIds = setOf(account.stableId)
        )
    }

    suspend fun revoke(agentId: McpAgentId) = store.revoke(agentId)

    suspend fun rename(agentId: McpAgentId, name: String): Result<McpAgent> = runCatching {
        store.rename(agentId, normalizedAgentName(name))
    }

    companion object {
        val DEFAULT_READ_SCOPES = setOf(
            McpScope.STATUS_READ,
            McpScope.TASK_READ,
            McpScope.SCRIPT_READ,
            McpScope.DEPENDENCY_READ,
            McpScope.ENV_READ_METADATA,
            McpScope.LOG_READ
        )
    }
}

@Serializable
private data class PersistedAgent(
    val id: String,
    val name: String,
    val tokenHash: String,
    val scopes: Set<String>,
    val allowedAccountIds: Set<String>,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long? = null
)

@Singleton
class AndroidMcpAgentStore @Inject constructor(
    @ApplicationContext context: Context
) : McpAgentStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()
    private var records = loadRecords()
    private val mutableAgents = MutableStateFlow(records.map { it.toPublic() })
    override val agents: StateFlow<List<McpAgent>> = mutableAgents.asStateFlow()

    override suspend fun issue(
        name: String,
        scopes: Set<McpScope>,
        accountIds: Set<String>
    ): McpIssuedCredential = withContext(Dispatchers.IO) {
        require(scopes.isNotEmpty()) { "An MCP Agent must have at least one scope" }
        require(accountIds.isNotEmpty()) { "An MCP Agent must be bound to an account" }
        val normalizedName = normalizedAgentName(name)
        mutex.withLock {
            check(records.size < MAX_AGENTS) { "The maximum number of MCP Agents has been reached" }
            val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
            val token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
            val record = PersistedAgent(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                tokenHash = hashToken(token),
                scopes = scopes.mapTo(linkedSetOf(), McpScope::name),
                allowedAccountIds = accountIds,
                createdAtEpochMs = System.currentTimeMillis()
            )
            records = records + record
            persistLocked()
            McpIssuedCredential(record.toPublic(), token)
        }
    }

    override suspend fun authenticate(token: String): McpAgent? = withContext(Dispatchers.IO) {
        if (!token.startsWith(TOKEN_PREFIX) || token.length > MAX_TOKEN_LENGTH) return@withContext null
        val candidate = hashToken(token)
        mutex.withLock {
            val index = records.indexOfFirst {
                MessageDigest.isEqual(
                    it.tokenHash.toByteArray(StandardCharsets.US_ASCII),
                    candidate.toByteArray(StandardCharsets.US_ASCII)
                )
            }
            if (index < 0) return@withLock null
            val current = records[index]
            val now = System.currentTimeMillis()
            if (current.lastUsedAtEpochMs == null || now - current.lastUsedAtEpochMs >= LAST_USED_WRITE_INTERVAL_MS) {
                val updated = current.copy(lastUsedAtEpochMs = now)
                records = records.toMutableList().also { it[index] = updated }
                persistLocked()
                updated.toPublic()
            } else {
                current.toPublic()
            }
        }
    }

    override suspend fun revoke(agentId: McpAgentId) = withContext(Dispatchers.IO) {
        mutex.withLock {
            records = records.filterNot { it.id == agentId.value }
            persistLocked()
        }
    }

    override suspend fun rename(agentId: McpAgentId, name: String): McpAgent = withContext(Dispatchers.IO) {
        val normalizedName = normalizedAgentName(name)
        mutex.withLock {
            val index = records.indexOfFirst { it.id == agentId.value }
            require(index >= 0) { "MCP Agent was not found" }
            val updated = records[index].copy(name = normalizedName)
            records = records.toMutableList().also { it[index] = updated }
            persistLocked()
            updated.toPublic()
        }
    }

    private fun loadRecords(): List<PersistedAgent> = preferences.getString(KEY_AGENTS, null)
        ?.let { runCatching { json.decodeFromString<List<PersistedAgent>>(it) }.getOrNull() }
        .orEmpty()

    private fun persistLocked() {
        check(preferences.edit().putString(KEY_AGENTS, json.encodeToString(records)).commit()) {
            "Unable to persist MCP Agent state"
        }
        mutableAgents.value = records.map { it.toPublic() }
    }

    private fun hashToken(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
    )

    private fun PersistedAgent.toPublic() = McpAgent(
        id = McpAgentId(id),
        name = name,
        scopes = scopes.mapNotNullTo(linkedSetOf()) { runCatching { McpScope.valueOf(it) }.getOrNull() },
        allowedAccountIds = allowedAccountIds,
        createdAtEpochMs = createdAtEpochMs,
        lastUsedAtEpochMs = lastUsedAtEpochMs
    )

    companion object {
        private const val PREFERENCES_NAME = "azureql_mcp_agents"
        private const val KEY_AGENTS = "agents_v1"
        private const val TOKEN_PREFIX = "azql_mcp_v1_"
        private const val TOKEN_BYTES = 32
        private const val MAX_TOKEN_LENGTH = 128
        private const val MAX_AGENTS = 20
        private const val LAST_USED_WRITE_INTERVAL_MS = 60_000L
    }
}

private fun normalizedAgentName(raw: String): String {
    val value = raw.trim()
    require(value.isNotEmpty()) { "Agent name cannot be empty" }
    require(value.length <= MAX_AGENT_NAME_LENGTH) {
        "Agent name cannot exceed $MAX_AGENT_NAME_LENGTH characters"
    }
    require(value.none(Char::isISOControl)) { "Agent name cannot contain control characters" }
    return value
}

const val MAX_AGENT_NAME_LENGTH = 80

sealed interface McpAuthorizationResult {
    data class Allowed(val context: McpCallContext) : McpAuthorizationResult
    data class Rejected(val statusCode: Int, val code: String) : McpAuthorizationResult
}

data class McpCallContext(
    val requestId: String,
    val agent: McpAgent,
    val accountId: String
)

@Singleton
class McpRequestLimiter @Inject constructor() {
    private data class Usage(var active: Int = 0, val attempts: ArrayDeque<Long> = ArrayDeque())
    private val lock = Any()
    private val usageByAgent = mutableMapOf<McpAgentId, Usage>()
    private val authenticationAttempts = mutableMapOf<String, ArrayDeque<Long>>()

    fun allowAuthenticationAttempt(peer: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val attempts = authenticationAttempts.getOrPut(peer) { ArrayDeque() }
            attempts.removeAll { now - it >= WINDOW_MS }
            if (attempts.size >= MAX_AUTH_ATTEMPTS_PER_MINUTE) false else {
                attempts.addLast(now)
                true
            }
        }

    fun acquire(agentId: McpAgentId, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val usage = usageByAgent.getOrPut(agentId) { Usage() }
            usage.attempts.removeAll { now - it >= WINDOW_MS }
            if (usage.active >= MAX_CONCURRENT || usage.attempts.size >= MAX_REQUESTS_PER_MINUTE) {
                false
            } else {
                usage.active++
                usage.attempts.addLast(now)
                true
            }
        }

    fun release(agentId: McpAgentId) = synchronized(lock) {
        usageByAgent[agentId]?.let { usage -> usage.active = (usage.active - 1).coerceAtLeast(0) }
    }

    companion object {
        const val MAX_CONCURRENT = 4
        private const val MAX_REQUESTS_PER_MINUTE = 60
        private const val MAX_AUTH_ATTEMPTS_PER_MINUTE = 10
        private const val WINDOW_MS = 60_000L
    }
}

@Serializable
data class McpAuditEvent(
    val timestampEpochMs: Long,
    val requestId: String,
    val agentId: String? = null,
    val agentName: String? = null,
    val tool: String? = null,
    val risk: String? = null,
    val outcome: String,
    val durationMs: Long? = null,
    val targetSummary: String? = null
)

interface McpAuditLogger {
    suspend fun record(event: McpAuditEvent)
}

@Singleton
class PersistentMcpAuditLogger @Inject constructor(
    @ApplicationContext context: Context
) : McpAuditLogger {
    private val preferences = context.getSharedPreferences("azureql_mcp_audit", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override suspend fun record(event: McpAuditEvent) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val existing = preferences.getString(KEY_EVENTS, null)
                ?.let { runCatching { json.decodeFromString<List<McpAuditEvent>>(it) }.getOrNull() }
                .orEmpty()
            val retained = (existing.asSequence().filter { it.timestampEpochMs >= cutoff } + event)
                .toList()
                .takeLast(MAX_EVENTS)
                .toList()
            preferences.edit().putString(KEY_EVENTS, json.encodeToString(retained)).commit()
            Unit
        }
    }

    companion object {
        private const val KEY_EVENTS = "events_v1"
        private const val MAX_EVENTS = 500
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

@Singleton
class McpHttpSecurity @Inject constructor(
    private val agentStore: McpAgentStore,
    private val accountIdentityProvider: ActiveAccountIdentityProvider,
    private val limiter: McpRequestLimiter,
    private val auditLogger: McpAuditLogger
) {
    suspend fun authorize(
        authorization: String?,
        host: String,
        origin: String?,
        peer: String,
        contentLength: Long?
    ): McpAuthorizationResult {
        val requestId = UUID.randomUUID().toString()
        if (!isLoopbackHost(normalizeHostHeader(host)) || !isAllowedOrigin(origin)) {
            reject(requestId, "HOST_OR_ORIGIN_REJECTED")
            return McpAuthorizationResult.Rejected(403, "HOST_OR_ORIGIN_REJECTED")
        }
        if (contentLength != null && contentLength > MAX_REQUEST_BODY_BYTES) {
            reject(requestId, "REQUEST_TOO_LARGE")
            return McpAuthorizationResult.Rejected(413, "REQUEST_TOO_LARGE")
        }
        val token = authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)?.trim()
        val agent = token?.let { agentStore.authenticate(it) }
        if (agent == null) {
            if (!limiter.allowAuthenticationAttempt(peer)) {
                reject(requestId, "AUTH_RATE_LIMITED")
                return McpAuthorizationResult.Rejected(429, "AUTH_RATE_LIMITED")
            }
            reject(requestId, "UNAUTHORIZED")
            return McpAuthorizationResult.Rejected(401, "UNAUTHORIZED")
        }
        val account = accountIdentityProvider.current()
        if (account == null || account.stableId !in agent.allowedAccountIds) {
            reject(requestId, "ACCOUNT_NOT_ALLOWED", agent)
            return McpAuthorizationResult.Rejected(403, "ACCOUNT_NOT_ALLOWED")
        }
        if (!limiter.acquire(agent.id)) {
            reject(requestId, "RATE_LIMITED", agent)
            return McpAuthorizationResult.Rejected(429, "RATE_LIMITED")
        }
        return McpAuthorizationResult.Allowed(McpCallContext(requestId, agent, account.stableId))
    }

    fun release(context: McpCallContext) = limiter.release(context.agent.id)

    private suspend fun reject(requestId: String, outcome: String, agent: McpAgent? = null) {
        auditLogger.record(
            McpAuditEvent(
                timestampEpochMs = System.currentTimeMillis(),
                requestId = requestId,
                agentId = agent?.id?.value,
                agentName = agent?.name,
                outcome = outcome
            )
        )
    }

    private fun isLoopbackHost(host: String): Boolean =
        host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1"

    private fun normalizeHostHeader(host: String): String = when {
        host.startsWith('[') -> host.substringAfter('[').substringBefore(']')
        else -> host.substringBefore(':')
    }

    private fun isAllowedOrigin(origin: String?): Boolean {
        if (origin.isNullOrBlank()) return true
        return runCatching { java.net.URI(origin).host }.getOrNull()?.let(::isLoopbackHost) == true
    }

    companion object {
        const val MAX_REQUEST_BODY_BYTES = 1_048_576L
        private const val BEARER_PREFIX = "Bearer "
    }
}
