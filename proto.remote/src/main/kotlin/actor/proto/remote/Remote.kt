package actor.proto.remote

import actor.proto.*
import io.grpc.Server
import io.grpc.ServerBuilder
import java.time.Duration

object Remote {
    private var _server: Server? = null
    private val Kinds: HashMap<String, Props> = HashMap()
    lateinit var endpointManagerPid: PID
    lateinit var activatorPid: PID
    fun getKnownKinds(): Set<String> = Kinds.keys
    fun registerKnownKind(kind: String, props: Props) {
        Kinds.put(kind, props)
    }

    fun getKnownKind(kind: String): Props {
        return Kinds.getOrElse(kind) {
            throw Exception("No Props found for kind '$kind'")
        }
    }

    fun start(hostname: String, port: Int, config: RemoteConfig = RemoteConfig()) {
        ProcessRegistry.registerHostResolver { pid -> RemoteProcess(pid) }
        _server = ServerBuilder.forPort(port).addService(EndpointReader()).build().start()
        val boundPort: Int = _server!!.port
        val boundAddress: String = "$hostname:$boundPort"
        val address: String = "${config.advertisedHostname ?: hostname}:${config.advertisedPort ?: boundPort}"
        ProcessRegistry.address = address
        spawnEndpointManager(config)
        spawnActivator()
        println("Starting Proto.Actor server on $boundAddress ($address)")
    }

    private fun spawnActivator() {
        val props = fromProducer { Activator() }
        activatorPid = spawnNamed(props, "activator")
    }

    private fun spawnEndpointManager(config: RemoteConfig) {
        val props = fromProducer { EndpointManager(config) }
        endpointManagerPid = spawn(props)
        EventStream.subscribe({
            if (it is EndpointTerminatedEvent) {
                endpointManagerPid.tell(it)
            }
        })
    }

    fun activatorForAddress(address: String): PID {
        return PID(address, "activator")
    }

    suspend fun spawnAsync(address: String, kind: String, timeout: Duration): PID {
        return spawnNamedAsync(address, "", kind, timeout)
    }

    suspend fun spawnNamedAsync(address: String, name: String, kind: String, timeout: Duration): PID {
        val activator: PID = activatorForAddress(address)
        val req = ActorPidRequest(kind, name)
        val res = activator.requestAsync<RemoteProtos.ActorPidResponse>(req, timeout)
        return res.pid
    }

    fun sendMessage(pid: PID, msg: Any, serializerId: Int) {
        val (message, sender) = when (msg) {
            is MessageEnvelope -> Pair(msg.message, msg.sender)
            else -> Pair(msg, null)
        }
        val env: RemoteDeliver = RemoteDeliver(message, pid, sender, serializerId)
        endpointManagerPid.tell(env)
    }
}
