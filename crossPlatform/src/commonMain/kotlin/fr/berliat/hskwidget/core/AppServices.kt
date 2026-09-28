package fr.berliat.hskwidget.core

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

open class AppServices {
    private val _status = MutableStateFlow<Status>(Status.NotInitialized)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var services: Map<String, FactoryEntry> = emptyMap()
    private val initMutex = Mutex()

    val appScope: CoroutineScope get() = get("appScope")

    init {
        registerNow("appScope", Priority.Highest) {
            CoroutineScope(SupervisorJob() + AppDispatchers.Main)
        }
    }

    sealed class Status {
        object NotInitialized : Status()
        object Initialized : Status()
        data class Ready(val partially: Boolean, val upToPrio: Priority) : Status()
        data class Failed(val error: Throwable) : Status()
    }

    data class FactoryEntry(
        val priority: Priority,
        val factory: suspend () -> Any,
        @Volatile var instance: Any? = null
    ) { fun isReady(): Boolean = instance != null }

    open class Priority(val priority: UInt) : Comparable<Priority> {
        constructor(other: Priority) : this(other.priority)

        override fun compareTo(other: Priority): Int = priority.compareTo(other.priority)
        override fun equals(other: Any?): Boolean = other is Priority && other.priority == this.priority
        override fun hashCode(): Int = priority.hashCode()
        override fun toString(): String = "Priority($priority)"

        object Highest : Priority(UInt.MIN_VALUE)
        object Standard : Priority(2u)
        object Lowest : Priority(UInt.MAX_VALUE)
    }

    /**
     * Register a service factory.
     */
    fun <T : Any> register(name: String, priority: Priority = Priority.Standard, factory: suspend () -> T) {
        if (isRegistered(name))
            throw Exception("Service $name Already registered")

        services = services + (name to FactoryEntry(priority, factory))
        _status.value = evaluateStatus()
    }

    /**
     * Register & Init blocking a service factory.
     */
    fun <T : Any> registerNow(name: String, priority: Priority = Priority.Standard, factory: () -> T) {
        if (isRegistered(name))
            throw Exception("Service $name Already registered")
        services = services + (name to FactoryEntry(priority, factory, factory()))
        _status.value = evaluateStatus()
    }

    /**
     * Initialize all services sequentially, in priority order (highest first).
     * Each service init logs its wall time so startup can be profiled per-service.
     */
    open fun init(upToLevel: Priority) {
        val currStatus = _status.value

        // If we are already ready for this level, don't restart everything
        if (currStatus is Status.Ready && currStatus.upToPrio >= upToLevel && !currStatus.partially) {
            return
        }

        // Force transition to Initialized to notify observers something is happening
        _status.value = Status.Initialized

        appScope.launch(AppDispatchers.IO) {
            val initStart = TimeSource.Monotonic.markNow()
            try {
                // Serialize overlapping init() calls; re-check readiness inside
                // the lock so a second call only builds what's still missing.
                initMutex.withLock {
                    // Snapshot the immutable map: safe to iterate even if a
                    // register() swaps in a new map concurrently.
                    val pending = services.entries
                        .filter { it.value.priority <= upToLevel && !it.value.isReady() }
                        .sortedBy { it.value.priority.priority } // highest first
                        .toList()
                    if (pending.isEmpty()) {
                        Logger.i(
                            tag = "AppServices",
                            messageString = "Init up to $upToLevel: nothing pending"
                        )
                    }
                    pending.forEach { (name, entry) ->
                        val serviceStart = TimeSource.Monotonic.markNow()
                        try {
                            entry.instance = entry.factory()
                            Logger.i(
                                tag = "AppServices",
                                messageString = "Service ready: $name prio=${entry.priority} in ${serviceStart.elapsedNow()}"
                            )
                        } catch (e: Throwable) {
                            Logger.e(tag = "AppServices", messageString = "Failed to initialize service: $name after ${serviceStart.elapsedNow()}", throwable = e)
                            throw e
                        }
                    }
                }

                _status.value = evaluateStatus()
                Logger.i(
                    tag = "AppServices",
                    messageString = "Init up to $upToLevel done in ${initStart.elapsedNow()} status=${_status.value}"
                )
            } catch (t: Throwable) {
                Logger.e(tag = "AppServices", messageString = "AppServices init failed", throwable = t)
                _status.value = Status.Failed(t)
            }
        }
    }

    /**
     * Suspend until the status is Ready, or timeout.
     * @return true if status is Ready, false otherwise.
     */
    suspend fun awaitReady(timeoutMs: Long = 5000): Boolean {
        return withTimeoutOrNull(timeoutMs.milliseconds) {
            status.first { it is Status.Ready }
            true
        } ?: false
    }

    fun isRegistered(name: String): Boolean {
        return services.containsKey(name)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> get(name: String): T {
        if (!isRegistered(name))
            throw IllegalArgumentException("No service registered with name $name")

        if (!services[name]!!.isReady())
            throw IllegalArgumentException("Service $name not instantiated")

        return services[name]!!.instance as T
    }

    private fun evaluateStatus(): Status {
        val levels = mutableMapOf<UInt, Boolean>()

        // Reads see an immutable snapshot, so no lock is needed here.
        services.entries.forEach {
            levels[it.value.priority.priority] = it.value.isReady() && (levels[it.value.priority.priority] ?: true)
        }

        // Extract max level and readiness level
        val maxPrio = levels.keys.maxOrNull()
        var minReady: UInt? = null

        val sortedLevels = levels.entries.sortedBy { it.key }
        for (entry in sortedLevels) {
            if (entry.value) {
                minReady = entry.key
            } else {
                break // Stop at the first "gap"
            }
        }

        return if (minReady == null) {
            Status.Initialized
        } else {
            Status.Ready(minReady != maxPrio, Priority(minReady))
        }
    }
}
