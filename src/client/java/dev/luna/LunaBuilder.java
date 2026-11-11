package dev.luna;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.mojang.blaze3d.platform.InputConstants;

public class LunaBuilder implements ClientModInitializer {
 public static final Gson GSON=new Gson();
 public static LunaBuilder INSTANCE;
 final ExecutorService io=Executors.newVirtualThreadPerTaskExecutor();
 final int controlPort=Integer.getInteger("lunabuilder.controlPort",28763);
 final HttpClient http=dev.luna.backend.LoopbackHttp.create();
 volatile JsonObject status=new JsonObject();
 volatile String error="";volatile String runtimeMessage="Starting background services...";
 dev.luna.runtime.BackgroundServices services;
 String draftPrompt="",draftStyle="",draftDimensions="48,48,48",draftSize="Medium",draftDetail="Detailed";boolean draftPrepare=false,draftInterior=true;
 String key="",botKey="",world="",dimension="";int port;long nextPoll;boolean polling,starting;IntegratedServer attached;
 volatile java.util.UUID grantedBot;volatile BlockPos allowedTarget;volatile long permitExpires;volatile boolean localStop,botInWorld;GameType previousBotMode;
 JsonObject report;String botError="",botTaskError="";final BuilderController builder=new BuilderController(this);
 KeyMapping open;
 static JsonObject obj(){return new JsonObject();}
 static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
 static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 static String str(JsonObject o,String k,String fallback){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
 @Override public void onInitializeClient(){INSTANCE=this;
 services=new dev.luna.runtime.BackgroundServices(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),controlPort,message->runtimeMessage=message);
 net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client->services.start().exceptionally(e->{runtimeMessage="Automatic startup failed: "+message(e);return null;}));
 net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->{end();io.execute(services::close);});
open=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lunabuilder.open",InputConstants.KEY_B,KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("lunabuilder","main"))));ClientTickEvents.END_CLIENT_TICK.register(this::tick);
 net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{if(screen instanceof TitleScreen)net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(net.minecraft.client.gui.components.Button.builder(Component.literal("LunaBuilder"),button->client.setScreenAndShow(new BuilderScreen(this))).bounds(5,5,100,20).build());});
 net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
  if(!level.isClientSide()&&player.getUUID().equals(grantedBot)){
   BlockPos p=level.getBlockState(hit.getBlockPos()).canBeReplaced()?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection());
   if(!permitted(level,p))return net.minecraft.world.InteractionResult.FAIL;
  }return net.minecraft.world.InteractionResult.PASS;
 });
 net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player,level,hand,p,face)->{
  if(!level.isClientSide()&&player.getUUID().equals(grantedBot)&&!permitted(level,p))return net.minecraft.world.InteractionResult.FAIL;
  return net.minecraft.world.InteractionResult.PASS;
 });
 }
 boolean permitted(net.minecraft.world.level.Level level,BlockPos p){return attached!=null&&level.getServer()==attached&&level.dimension().identifier().toString().equals(dimension)&&System.currentTimeMillis()<permitExpires&&p.equals(allowedTarget);}

 void tick(Minecraft mc){
  while(open.consumeClick())mc.setScreenAndShow(new BuilderScreen(this));
  if(attached!=null&&(mc.getSingleplayerServer()!=attached||mc.level==null||!mc.level.dimension().identifier().toString().equals(dimension)))end();
  if(!botKey.isEmpty()&&mc.level!=null)builder.tick(mc);
  long now=System.currentTimeMillis();if(now<nextPoll||polling)return;nextPoll=now+250;
  if(!key.isEmpty()&&attached!=null){polling=true;JsonObject body=obj();body.addProperty("botInWorld",botInWorld);if(report!=null){body.add("result",report);report=null;}final String current=key;request("/host/poll",body,current).whenComplete((r,e)->mc.execute(()->{polling=false;if(!key.equals(current))return;if(e!=null){allowedTarget=null;error=message(e);return;}status=r;allowedTarget=!localStop&&r.has("allowedTarget")&&!r.get("allowedTarget").isJsonNull()?pos(r.getAsJsonArray("allowedTarget")):null;permitExpires=System.currentTimeMillis()+1000;error="";if(r.has("authorizeBot")&&!r.get("authorizeBot").isJsonNull())authorize(r.get("authorizeBot").getAsString());if(r.has("command")&&!r.get("command").isJsonNull())hostCommand(r.getAsJsonObject("command"));}));}
  else if(!botKey.isEmpty()){
   polling=true;JsonObject body=obj();if(!botError.isEmpty()){body.addProperty("error",botError);body.addProperty("task",botTaskError);botError="";}final String current=botKey;
   request("/bot/poll",body,current).whenComplete((r,e)->mc.execute(()->{polling=false;if(!botKey.equals(current))return;if(e!=null){builder.stop(mc);error=message(e);if(mc.level!=null)mc.disconnectWithSavingScreen();botKey="";return;}builder.accept(r);error="";}));
  }
 }
 CompletableFuture<JsonObject> request(String route,JsonObject body,String secret){return CompletableFuture.supplyAsync(()->{try{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+controlPort+route)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));if(!secret.isEmpty())b.header("Authorization","Bearer "+secret);
  var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(response.body().length()>8_000_000)throw new IllegalStateException("Companion response exceeds limit");JsonObject r=JsonParser.parseString(response.body()).getAsJsonObject();if(r.has("error"))throw new IllegalStateException(r.get("error").getAsString());return r;
 }catch(Exception e){throw new CompletionException(e);}},io);}
 static String message(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
 void startHost(){
 var readiness=services.start();if(!readiness.isDone()){error="Starting background services...";readiness.whenComplete((v,e)->Minecraft.getInstance().execute(()->{if(e!=null)error="Automatic startup failed: "+message(e);else{error="";startHost();}}));return;}
 if(readiness.isCompletedExceptionally()){error=runtimeMessage;return;}
 var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||mc.player==null||!mc.player.getAbilities().instabuild){error="Open your own creative singleplayer world first.";return;}if(starting)return;if(!key.isEmpty()&&error.isEmpty())return;if(!key.isEmpty())end();starting=true;error="Connecting to companion...";var dim=mc.level.dimension();
  server.execute(()->{try{
   if(!server.isPublished()&&!server.publishServer(MinecraftServer.MultiplayerScope.LAN,false,net.minecraft.util.HttpUtil.getAvailablePort()))throw new IllegalStateException("Could not publish integrated server to LAN");
   int actualPort=server.getPort();Path file=server.getWorldPath(LevelResource.ROOT).resolve("lunabuilder-world-id.txt");JsonObject registry=registry();
   CompletableFuture.supplyAsync(()->{try{if(!Files.exists(file))Files.writeString(file,UUID.randomUUID().toString(),StandardOpenOption.CREATE_NEW);return Files.readString(file).trim()+"-"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(file.toAbsolutePath().normalize().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,16);}catch(Exception e){throw new CompletionException(e);}},io).thenAccept(id->mc.execute(()->{
    if(mc.getSingleplayerServer()!=server){starting=false;return;}attached=server;world=id;dimension=dim.identifier().toString();port=actualPort;key=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");String current=key;JsonObject b=obj();b.addProperty("world",world);b.addProperty("dimension",dimension);b.addProperty("port",port);b.addProperty("secret",key);b.add("registry",registry);
    request("/host/session",b,"").whenComplete((r,e)->mc.execute(()->{starting=false;if(!key.equals(current))return;if(e!=null){error=message(e);key="";attached=null;}else{status=r;error="";}}));
   })).exceptionally(e->{mc.execute(()->{starting=false;error=message(e);});return null;});
  }catch(Exception e){mc.execute(()->{starting=false;error=message(e);});}});
 }
 @SuppressWarnings({"rawtypes","unchecked"}) static JsonObject registry(){JsonObject r=obj();for(var block:BuiltInRegistries.BLOCK){String id=BuiltInRegistries.BLOCK.getKey(block).toString();if(!id.startsWith("minecraft:"))continue;JsonObject d=obj(),properties=obj();d.addProperty("blockEntity",block.defaultBlockState().hasBlockEntity());for(Property p:block.getStateDefinition().getProperties()){JsonArray values=new JsonArray();for(Object v:p.getPossibleValues())values.add(p.getName((Comparable)v));properties.add(p.getName(),values);}d.add("properties",properties);r.add(id,d);}return r;}
 void authorize(String name){final var server=attached;if(server==null||name.isEmpty())return;server.execute(()->{var player=server.getPlayerList().getPlayerByName(name);if(player==null){botInWorld=false;return;}var remote=player.connection.getRemoteAddress();if(remote instanceof InetSocketAddress addr&&addr.getAddress().isLoopbackAddress()&&player.level().dimension().identifier().toString().equals(dimension)){botInWorld=true;if(grantedBot==null){grantedBot=player.getUUID();previousBotMode=player.gameMode.getGameModeForPlayer();}if(player.getUUID().equals(grantedBot))player.setGameMode(GameType.CREATIVE);}});}
 void hostCommand(JsonObject c){var server=attached;if(server==null)return;String current=key;server.execute(()->{JsonObject r=obj();r.addProperty("id",str(c,"id",""));r.addProperty("type",str(c,"type",""));try{
   if(server!=attached||!key.equals(current))return;ServerLevel level=server.getLevel(Minecraft.getInstance().level.dimension());if(level==null)throw new IllegalStateException("Dimension changed");
   if(str(c,"type","").equals("survey")){r.add("origin",survey(level,c));}
   else {r.addProperty("start",c.get("start").getAsInt());JsonArray blocks=new JsonArray();JsonArray positions=c.getAsJsonArray("positions");if(positions.size()>128)throw new IllegalStateException("Inspection limit");for(JsonElement el:positions){BlockPos p=pos(el.getAsJsonArray());JsonObject b=obj();b.add("p",pos(p));boolean loaded=level.hasChunkAt(p)&&p.getY()>=level.getMinY()&&p.getY()<=level.getMaxY();var state=loaded?level.getBlockState(p):net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();b.addProperty("state",BlockStateParser.serialize(state));b.addProperty("protected",!loaded||state.hasBlockEntity()||!state.getFluidState().isEmpty());b.addProperty("replaceable",state.canBeReplaced());JsonArray supports=new JsonArray();if(loaded){for(Direction dir:Direction.values()){BlockPos q=p.relative(dir);if(level.hasChunkAt(q)){var neighbor=level.getBlockState(q);if(!neighbor.canBeReplaced()&&!neighbor.hasBlockEntity()&&neighbor.getFluidState().isEmpty())supports.add(pos(q));}}}b.add("supports",supports);blocks.add(b);}r.add("blocks",blocks);}
  }catch(Exception e){r.addProperty("error",message(e));}Minecraft.getInstance().execute(()->{if(key.equals(current))report=r;});});}
 JsonElement survey(ServerLevel level,JsonObject c){var mc=Minecraft.getInstance();if(mc.player==null)return JsonNull.INSTANCE;boolean selected=c.has("anchor")&&!c.get("anchor").isJsonNull();BlockPos player=selected?pos(c.getAsJsonArray("anchor")):mc.player.blockPosition();JsonObject bounds=c.getAsJsonObject("compiled");BlockPos min=pos(bounds.getAsJsonArray("min")),max=pos(bounds.getAsJsonArray("max"));int rotation=c.get("rotation").getAsInt();int w=max.getX()-min.getX()+1,l=max.getZ()-min.getZ()+1;if(rotation%2==1){int t=w;w=l;l=t;}
  int ox=c.getAsJsonArray("offset").get(0).getAsInt(),oz=c.getAsJsonArray("offset").get(1).getAsInt();
  double best=Double.MAX_VALUE;BlockPos chosen=null;
  // At most 9 candidates, sampled grids; exact occupied-target inspection runs before building.
  for(int cx=selected?0:-1;cx<=(selected?0:1);cx++)for(int cz=selected?0:-1;cz<=(selected?0:1);cz++){
   int x0=player.getX()+(selected?0:8)+cx*16+ox,z0=player.getZ()+(selected?0:8)+cz*16+oz;int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;boolean safe=true;int step=Math.max(1,Math.max(w,l)/12);
   for(int x=0;x<w&&safe;x+=step)for(int z=0;z<l;z+=step){BlockPos p=new BlockPos(x0+x,player.getY(),z0+z);if(!level.hasChunkAt(p)){safe=false;break;}int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())-1;var state=level.getBlockState(new BlockPos(p.getX(),y,p.getZ()));String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(state.hasBlockEntity()||!state.getFluidState().isEmpty()||!id.matches("minecraft:(grass_block|dirt|stone|sand|gravel|snow_block|podzol|mycelium|moss_block)")){safe=false;break;}low=Math.min(low,y);high=Math.max(high,y);}
   if(!safe||high-low>0||high+max.getY()-min.getY()+2>level.getMaxY())continue;double score=Math.hypot(x0-player.getX(),z0-player.getZ());if(score<best){best=score;int tx=rotation==0?min.getX():rotation==1?-max.getZ():rotation==2?-max.getX():min.getZ();int tz=rotation==0?min.getZ():rotation==1?min.getX():rotation==2?-max.getZ():-max.getX();chosen=new BlockPos(x0-tx,high+1-min.getY(),z0-tz);}
  }return chosen==null?JsonNull.INSTANCE:pos(chosen);
 }
 void action(JsonObject b){if(key.isEmpty()){error="Start local builder first.";return;}String current=key;request("/action",b,current).whenComplete((r,e)->Minecraft.getInstance().execute(()->{if(!key.equals(current))return;if(e!=null)error=message(e);else{status=r;error="";String action=str(b,"action","");if(action.equals("resume")||action.equals("start")||action.equals("undo"))localStop=false;}}));}
 void action(String name){if(name.equals("pause")||name.equals("cancel")){localStop=true;allowedTarget=null;permitExpires=0;}JsonObject b=obj();b.addProperty("action",name);action(b);}
 void pairBot(String code){
 var readiness=services.start();if(!readiness.isDone()){error="Starting background services...";readiness.whenComplete((v,e)->Minecraft.getInstance().execute(()->{if(e!=null)error=message(e);else pairBot(code);}));return;}
 if(readiness.isCompletedExceptionally()){error=runtimeMessage;return;}
 var mc=Minecraft.getInstance();if(mc.hasSingleplayerServer()){error="Pair in a separate authenticated Fabric instance, from its title screen.";return;}JsonObject b=obj();b.addProperty("code",code.trim());b.addProperty("name",mc.getUser().getName());request("/bot/pair",b,"").whenComplete((r,e)->mc.execute(()->{if(e!=null){error=message(e);return;}botKey=r.get("key").getAsString();world=r.get("world").getAsString();port=r.get("port").getAsInt();String addr="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(addr),new ServerData("LunaBuilder local world",addr,ServerData.Type.LAN),false,null);error="Connecting builder to "+addr;}));}
 void end(){allowedTarget=null;permitExpires=0;var server=attached;var bot=grantedBot;var mode=previousBotMode;if(server!=null&&bot!=null)server.execute(()->{var p=server.getPlayerList().getPlayer(bot);if(p!=null){if(mode!=null)p.setGameMode(mode);p.connection.disconnect(Component.literal("LunaBuilder host session ended"));}});grantedBot=null;botInWorld=false;previousBotMode=null;String old=key;if(!old.isEmpty())request("/host/end",obj(),old);key="";attached=null;world="";report=null;status=obj();error="World session ended. Pairing invalidated.";}
}
