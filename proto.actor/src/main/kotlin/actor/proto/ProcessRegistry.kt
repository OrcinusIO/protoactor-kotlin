package actor.proto

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object ProcessRegistry {
    private val NoHost: String = "nonhost"
    private val hostResolvers: MutableList<(Protos.PID) -> Process> = mutableListOf()
    private val processLookup: ConcurrentHashMap<String, Process> = ConcurrentHashMap()
    private val sequenceId: AtomicInteger = AtomicInteger(0)
    var address: String = NoHost

    fun registerHostResolver(resolver: (Protos.PID) -> Process) {
        hostResolvers.add(resolver)
    }

    fun get(pid: Protos.PID): Process {
        if (pid.address != NoHost && pid.address != address) {
            hostResolvers
                    .mapNotNull { it(pid) }
                    .forEach { return it }

            throw Exception("Unknown host")
        }
        return processLookup.getOrDefault(pid.id, DeadLetterProcess)
        //return processLookup.tryGetValue(pid.id) ?: DeadLetterProcess
    }

    fun add(id: String, process: Process): Protos.PID {
        val pid = PID(address, id)
        pid.cachedProcess_ = process //we know what pid points to what process here
        processLookup.put(pid.id, process)
        return pid
    }

    fun remove(pid: Protos.PID) {
        processLookup.remove(pid.id)
    }

    fun nextId(): String {
        val counter: Int = sequenceId.incrementAndGet()
        return "$" + counter
    }
}

