package lighttunnel.openapi.args

import lighttunnel.openapi.http.HttpPlugin
import lighttunnel.openapi.http.HttpTunnelRequestInterceptor

class HttpTunnelArgs(
    val bindAddr: String? = null,
    val bindPort: Int = 80,
    val httpTunnelRequestInterceptor: HttpTunnelRequestInterceptor? = null,
    val httpPlugin: HttpPlugin? = null
)