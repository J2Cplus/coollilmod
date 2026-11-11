package dev.luna.runtime;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Starts the local companion off the render thread; shared services use expiring per-client leases. */
public final class BackgroundServices implements AutoCloseable {
 private final Path gameDir;
 private final int port;
 private final Consumer<String> report;
 private final String clientId=UUID.randomUUID().toString(),secret=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"LunaBuilder services");t.setDaemon(true);return t;});
 private CompletableFuture<Void> startup;
 private volatile boolean closed,leased;
 private final Thread shutdownHook;
 public BackgroundServices(Path gameDir,int port,Consumer<String> report){this.gameDir=gameDir.toAbsolutePath().normalize();this.port=port;this.report=report;shutdownHook=new Thread(this::release,"LunaBuilder service release");Runtime.getRuntime().addShutdownHook(shutdownHook);}
 public synchronized CompletableFuture<Void> start(){
  if(startup!=null)return startup;
  startup=CompletableFuture.runAsync(()->{try{launch();}catch(Exception e){throw new CompletionException(e);}},worker);
  worker.scheduleWithFixedDelay(()->{if(leased&&!closed)try{show(post("/runtime/lease"));}catch(Exception e){report.accept("Background companion disconnected. Restart Minecraft or check config/lunabuilder/logs.");}},5,5,TimeUnit.SECONDS);
  return startup;
 }
 private void launch()throws Exception {
  report.accept("Starting LunaBuilder background services...");
  String user=hex(System.getProperty("user.home","").getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,12);
  Path lockFile=Path.of(System.getProperty("java.io.tmpdir"),"lunabuilder-"+user+"-"+port+".lock");
  try(FileChannel channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
   FileLock lock=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
   while(lock==null&&!closed){try{lock=channel.tryLock();}catch(OverlappingFileLockException ignored){}if(lock==null){if(System.nanoTime()>deadline)throw new IOException("Another LunaBuilder instance is still starting");Thread.sleep(150);}}
   if(lock==null)return;
   try{
    JsonObject health=health();
    if(health==null){
     if(closed)return;
     Path root=extract();Path settings=gameDir.resolve("config/lunabuilder");Files.createDirectories(settings.resolve("logs"));
     Path config=settings.resolve("companion.json");if(!Files.exists(config))Files.copy(root.resolve("companion/config.example.json"),config);
     boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
     String node=windows?root.resolve("runtime/node.exe").toString():System.getProperty("lunabuilder.node","node");
     ProcessBuilder pb=new ProcessBuilder(node,root.resolve("companion/server.mjs").toString());pb.directory(root.resolve("companion").toFile());
     pb.redirectOutput(ProcessBuilder.Redirect.appendTo(settings.resolve("logs/companion.log").toFile()));pb.redirectError(ProcessBuilder.Redirect.appendTo(settings.resolve("logs/companion-error.log").toFile()));
     var env=pb.environment();env.put("LUNA_MANAGED","1");env.put("LUNA_CONTROL_PORT",Integer.toString(port));env.put("LUNA_CONFIG",config.toString());env.put("LUNA_DATA",settings.resolve("data").toString());env.put("LUNA_OWNER_PID",Long.toString(ProcessHandle.current().pid()));
     Process process=pb.start();
     for(int i=0;i<100&&!closed;i++){health=health();if(health!=null)break;if(!process.isAlive())throw new IOException("Companion exited. See config/lunabuilder/logs/companion-error.log");Thread.sleep(150);}
     if(health==null)throw new IOException("Companion startup timed out. See config/lunabuilder/logs.");
    }
    if(closed)return;
    if(!health.has("provider")||!health.get("provider").getAsString().equals("ollama"))throw new IOException("Existing companion is old or uses a remote provider. Stop that companion once, then restart Minecraft for local Ollama.");
    if(health.has("managed")&&health.get("managed").getAsBoolean()){show(post("/runtime/lease"));leased=true;if(closed)release();}
    else report.accept("Using your already-running LunaBuilder companion.");
   }finally{lock.release();}
  }
 }
 private JsonObject health()throws Exception {
  try{var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IOException("Port "+port+" is occupied by another service");JsonObject j=JsonParser.parseString(response.body()).getAsJsonObject();if(!j.has("name")||!j.get("name").getAsString().equals("LunaBuilder")||!j.has("version")||j.get("version").getAsInt()!=1)throw new IOException("Port "+port+" is occupied by an incompatible service");return j;
  }catch(java.net.ConnectException e){return null;}catch(java.net.http.HttpConnectTimeoutException e){return null;}
 }
 private JsonObject post(String route)throws Exception {
  String body="{\"clientId\":\""+clientId+"\"}";
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).timeout(Duration.ofSeconds(2)).header("Content-Type","application/json").header("Authorization","Bearer "+secret).POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IOException("Runtime lease was rejected");return JsonParser.parseString(response.body()).getAsJsonObject();
 }
 private void show(JsonObject state){String message=state.has("ollamaError")?state.get("ollamaError").getAsString():"";String phase=state.has("ollamaState")?state.get("ollamaState").getAsString():"";
  if(!message.isEmpty())report.accept("Ollama: "+message);
  else if(phase.equals("ready"))report.accept("Background services ready. Ollama: "+state.get("modelId").getAsString());
  else report.accept("Companion ready; starting local Ollama and detecting your installed models...");
 }
 private Path extract()throws Exception {
  byte[] bytes;try(InputStream input=resource("manifest.properties")){bytes=input.readAllBytes();}
  Properties manifest=new Properties();manifest.load(new ByteArrayInputStream(bytes));
  Path target=gameDir.resolve("config/lunabuilder/runtime/"+hex(bytes).substring(0,16));Files.createDirectories(target);
  String[] names=manifest.getProperty("files","").split(",");if(names.length<5||names.length>64)throw new IOException("Invalid embedded runtime manifest");
  for(String name:names){Path file=target.resolve(name).normalize();if(!file.startsWith(target)||name.isBlank())throw new IOException("Invalid runtime resource path");String expected=manifest.getProperty(name);if(expected==null||!expected.matches("[a-f0-9]{64}"))throw new IOException("Missing runtime digest");
   if(Files.isRegularFile(file)&&hash(file).equals(expected))continue;
   Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");try(InputStream source=resource(name)){Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);}
   if(!hash(tmp).equals(expected)){Files.deleteIfExists(tmp);throw new IOException("Embedded runtime checksum mismatch");}Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
  }return target;
 }
 private static InputStream resource(String name)throws IOException {InputStream in=BackgroundServices.class.getResourceAsStream("/lunabuilder-runtime/"+name);if(in==null)throw new IOException("Embedded runtime missing: "+name);return in;}
 private static String hash(Path file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
 private static String hex(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void release(){if(!leased)return;try{post("/runtime/release");}catch(Exception ignored){}finally{leased=false;}}
 @Override public void close(){closed=true;release();worker.shutdownNow();try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}}
}
