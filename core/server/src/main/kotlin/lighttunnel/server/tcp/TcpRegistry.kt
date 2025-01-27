package lighttunnel.server.tcp

import lighttunnel.base.proto.ProtoException
import lighttunnel.base.utils.loggerDelegate
import lighttunnel.server.tcp.impl.TcpFdImpl
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class TcpRegistry {
    private val logger by loggerDelegate()

    private val portTcpFds = hashMapOf<Int, TcpFdImpl>()
    private val lock = ReentrantReadWriteLock()

    @Throws(ProtoException::class)
    fun register(port: Int, fd: TcpFdImpl) {
        if (isRegistered(port)) {
            throw ProtoException("port($port) already used")
        }
        lock.write { portTcpFds[port] = fd }
        logger.debug("Start Tunnel: {}, Extras", fd.tunnelRequest, fd.tunnelRequest.extras)
    }

    fun unregister(port: Int): TcpFdImpl? = lock.write {
        unsafeUnregister(port)
        portTcpFds.remove(port)?.apply { close() }
    }

    fun depose() = lock.write {
        portTcpFds.forEach { (port, _) -> unsafeUnregister(port) }
        portTcpFds.clear()
    }

    fun isRegistered(port: Int): Boolean = lock.read { portTcpFds.contains(port) }

    fun getTcpFd(port: Int): TcpFdImpl? = lock.read { portTcpFds[port] }

    fun getTcpFdList() = lock.read { portTcpFds.values.toList() }

    fun forceOff(port: Int) = getTcpFd(port)?.apply { forceOff() }

    private fun unsafeUnregister(port: Int) {
        portTcpFds[port]?.also {
            it.close()
            logger.debug("Shutdown Tunnel: {}", it.sessionChannels.tunnelRequest)
        }
    }

}
