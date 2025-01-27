package lighttunnel.ext.base.httpserver

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import lighttunnel.base.utils.loggerDelegate


class HttpServer(
    bossGroup: NioEventLoopGroup,
    workerGroup: NioEventLoopGroup,
    private val bindAddr: String?,
    private val bindPort: Int,
    private val sslContext: SslContext? = null,
    private val maxContentLength: Int = 1024 * 1024 * 8,
    routing: RouterMappings.() -> Unit
) {
    private val logger by loggerDelegate()
    private val serverBootstrap = ServerBootstrap()
    private val isHttps: Boolean get() = sslContext != null
    private val routerMappings = RouterMappings()
    private var bindChannelFuture: ChannelFuture? = null

    init {
        serverBootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel?) {
                    ch ?: return
                    if (sslContext != null) {
                        ch.pipeline().addFirst(
                            "ssl", SslHandler(sslContext.newEngine(ch.alloc()))
                        )
                    }
                    ch.pipeline()
                        .addLast("codec", HttpServerCodec())
                        .addLast("httpAggregator", HttpObjectAggregator(maxContentLength))
                        .addLast("handler", HttpServerChannelHandler { routerMappings.doHandle(it) })
                }
            })
        routerMappings.routing()
    }

    fun start() {
        bindChannelFuture = if (bindAddr == null) {
            serverBootstrap.bind(bindPort)
        } else {
            serverBootstrap.bind(bindAddr, bindPort)
        }
        logger.info(
            "Serving {} on {} port {}",
            if (isHttps) "https" else "http",
            bindAddr ?: "::",
            bindPort
        )
    }

    fun depose() {
        bindChannelFuture?.channel()?.close()
    }

}
