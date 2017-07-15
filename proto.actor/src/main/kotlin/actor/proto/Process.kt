package actor.proto

import actor.proto.mailbox.SystemMessage
import actor.proto.mailbox.Mailbox

abstract class Process {
    abstract fun sendUserMessage(pid: Protos.PID, message: Any)
    open fun stop(pid: Protos.PID) = sendSystemMessage(pid, Stop)

    abstract fun sendSystemMessage(pid: Protos.PID, message: SystemMessage)
}


open class LocalProcess(private val mailbox: Mailbox) : Process() {
    private var isDead: Boolean = false
    override fun sendUserMessage(pid: Protos.PID, message: Any) {
        mailbox.postUserMessage(message)
    }

    override fun sendSystemMessage(pid: Protos.PID, message: SystemMessage) {
        mailbox.postSystemMessage(message)
    }

    override fun stop(pid: Protos.PID) {
        super.stop(pid)
        isDead = true
    }
}