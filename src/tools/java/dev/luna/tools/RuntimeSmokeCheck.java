package dev.luna.tools;
import dev.luna.runtime.BackgroundServices;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.Duration;import java.util.*;import java.util.concurrent.*;
import com.google.gson.*;
/** Exercises the packaged Java launcher and real companion process without a Minecraft window. */
public final class RuntimeSmokeCheck {
 static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
 static JsonObject health(int port)throws Exception {var r=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.ofString());return JsonParser.parseString(r.body()).getAsJsonObject();}
 public static void main(String[] args)throws Exception {
  Path temp=Files.createTempDirectory("LunaBuilder runtime smoke with spaces ");int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
  var a=new BackgroundServices(temp.resolve("host profile"),port,m->System.out.println("host: "+m));var b=new BackgroundServices(temp.resolve("builder profile"),port,m->System.out.println("builder: "+m));
  try{
   a.start().get(40,TimeUnit.SECONDS);if(health(port).get("clients").getAsInt()!=1)throw new AssertionError("First lease missing");
   b.start().get(15,TimeUnit.SECONDS);if(health(port).get("clients").getAsInt()!=2)throw new AssertionError("Second client did not share companion");
   a.close();Thread.sleep(1200);if(health(port).get("clients").getAsInt()!=1)throw new AssertionError("Closing host stopped builder's companion");
   b.close();boolean stopped=false;for(int i=0;i<40;i++){try{health(port);}catch(java.net.ConnectException e){stopped=true;break;}Thread.sleep(150);}if(!stopped)throw new AssertionError("Companion survived its last lease");
   System.out.println("PASS: embedded extraction, automatic real Node startup, shared clients, path spaces, and last-client cleanup.");
  }finally{a.close();b.close();try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
