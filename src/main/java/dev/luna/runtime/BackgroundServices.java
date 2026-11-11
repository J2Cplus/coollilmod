package dev.luna.runtime;
import com.google.gson.*;import dev.luna.backend.LocalBackend;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.Duration;import java.util.concurrent.*;import java.util.function.Consumer;
/** In-process Java backend. No executable extraction, process launches, shells or downloaded code. */
public final class BackgroundServices implements AutoCloseable {
 private static final Object START_LOCK=new Object();
 private final Path gameDir;private final int port;private final Consumer<String> report;private final ExecutorService worker=Executors.newVirtualThreadPerTaskExecutor();
 private final HttpClient http=dev.luna.backend.LoopbackHttp.create();private CompletableFuture<Void> startup;private volatile LocalBackend owned;private volatile boolean closed;
 public BackgroundServices(Path gameDir,int port,Consumer<String> report){this.gameDir=gameDir;this.port=port;this.report=report;}
 public synchronized CompletableFuture<Void> start(){if(startup==null)startup=CompletableFuture.runAsync(()->{try{synchronized(START_LOCK){if(closed)return;if(!existing()){try{owned=new LocalBackend(gameDir.resolve("config/lunabuilder-java"),port);}catch(BindException race){if(!existing())throw race;}}}report.accept("Java backend ready. Open your installed Ollama app for local AI; no helper programs are launched.");}catch(Exception e){throw new CompletionException(e);}},worker);return startup;}
 private boolean existing()throws Exception {try{var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalArgumentException("Control port is occupied by another service");JsonObject health=JsonParser.parseString(response.body()).getAsJsonObject();if(!health.has("name")||!health.get("name").getAsString().equals("LunaBuilder")||!health.has("backend")||!health.get("backend").getAsString().equals("java-in-process"))throw new IllegalArgumentException("An older/external companion occupies the control port. Stop it before using the Java-only build.");return true;}catch(ConnectException e){return false;}}
 @Override public void close(){closed=true;if(owned!=null)owned.close();worker.shutdownNow();}
}
