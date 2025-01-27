package lighttunnel.ext.server

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.*
import lighttunnel.base.TunnelRequest
import lighttunnel.base.utils.basicAuthorization
import lighttunnel.ext.base.*
import lighttunnel.server.http.HttpContext
import lighttunnel.server.http.HttpTunnelRequestInterceptor
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import java.util.*

class HttpTunnelRequestInterceptorDefaultImpl : HttpTunnelRequestInterceptor {

    companion object {
        /** remote_addr 魔法值 */
        private const val MAGIC_VALUE_REMOTE_ADDR = "\$remote_addr"
    }

    override fun doHttpRequest(ctx: HttpContext, httpRequest: HttpRequest, tunnelRequest: TunnelRequest): Boolean {
        val localAddress = ctx.localAddress ?: return false
        val remoteAddress = ctx.remoteAddress ?: return false
        handleRewriteHttpHeaders(localAddress, remoteAddress, tunnelRequest, httpRequest)
        handleWriteHttpHeaders(localAddress, remoteAddress, tunnelRequest, httpRequest)
        return tunnelRequest.enableBasicAuth && handleHttpBasicAuth(ctx, tunnelRequest, httpRequest)
    }

    private fun handleHttpBasicAuth(chain: HttpContext, tunnelRequest: TunnelRequest, httpRequest: HttpRequest): Boolean {
        val account = httpRequest.basicAuthorization
        val username = tunnelRequest.basicAuthUsername
        val password = tunnelRequest.basicAuthPassword
        if (account == null || username != account.first || password != account.second) {
            val content = HttpResponseStatus.UNAUTHORIZED.toString().toByteArray(StandardCharsets.UTF_8)
            chain.write(
                DefaultHttpResponse(httpRequest.protocolVersion(), HttpResponseStatus.UNAUTHORIZED).apply {
                    headers().add(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"${tunnelRequest.basicAuthRealm}\"")
                    headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    headers().add(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES)
                    headers().add(HttpHeaderNames.DATE, Date().toString())
                    headers().add(HttpHeaderNames.CONTENT_LENGTH, content.size)
                }
            )
            chain.write(DefaultHttpContent(Unpooled.wrappedBuffer(content)))
            chain.write(LastHttpContent.EMPTY_LAST_CONTENT, flush = true)
            return true
        }
        return false
    }

    private fun handleRewriteHttpHeaders(
        localAddress: SocketAddress,
        remoteAddress: SocketAddress,
        tunnelRequest: TunnelRequest,
        httpRequest: HttpRequest
    ) = handleProxyHttpHeaders(
        tunnelRequest.pxyAddHeaders,
        localAddress,
        remoteAddress,
        tunnelRequest,
        httpRequest
    ) { name, value -> add(name, value) }

    private fun handleWriteHttpHeaders(
        localAddress: SocketAddress,
        remoteAddress: SocketAddress,
        tunnelRequest: TunnelRequest,
        httpRequest: HttpRequest
    ) = handleProxyHttpHeaders(
        tunnelRequest.pxySetHeaders,
        localAddress,
        remoteAddress,
        tunnelRequest,
        httpRequest
    ) { name, value -> if (contains(name)) set(name, value) }

    @Suppress("UNUSED_PARAMETER")
    private inline fun handleProxyHttpHeaders(
        pxyHeaders: Map<String, String>,
        localAddress: SocketAddress,
        remoteAddress: SocketAddress,
        tunnelRequest: TunnelRequest,
        httpRequest: HttpRequest,
        apply: HttpHeaders.(name: String, value: String) -> Unit
    ) {
        if (pxyHeaders.isEmpty()) {
            return
        }
        for (it in pxyHeaders.entries) {
            val name = it.key
            // 需要处理魔法值
            val value = when (it.value) {
                MAGIC_VALUE_REMOTE_ADDR -> if (remoteAddress is InetSocketAddress) remoteAddress.address.toString() else null
                else -> it.value
            } ?: continue
            httpRequest.headers().apply(name, value)
        }
    }


}
