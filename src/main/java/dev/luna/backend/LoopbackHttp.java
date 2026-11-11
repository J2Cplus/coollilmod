package dev.luna.backend;
import java.net.*;import java.net.http.*;import java.io.IOException;import java.time.Duration;import java.util.*;
/** Never routes local control/model data through a proxy or follows a redirect. */
public final class LoopbackHttp {
 private LoopbackHttp(){}
 public static HttpClient create(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){if(!"http".equals(uri.getScheme())||uri.getHost()==null||!Set.of("127.0.0.1","localhost","[::1]","::1").contains(uri.getHost()))throw new IllegalArgumentException("Non-loopback HTTP is not permitted");return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException e){}}).build();}
}
