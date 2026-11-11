package dev.luna.tools;
import java.nio.file.*;import java.util.*;import java.lang.reflect.Proxy;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.*;
import net.fabricmc.loader.impl.metadata.*;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.knot.Knot;
/** Uses the actual Fabric Loader scanner and resolver against a mods folder containing exactly one JAR. */
public final class SingleJarCheck {
 public static void main(String[] args)throws Exception {
  Path temp=Files.createTempDirectory("LunaBuilder single jar check ");Path mods=Files.createDirectories(temp.resolve("mods"));Files.copy(Path.of(args[0]),mods.resolve("LunaBuilder.jar"));
  try{
   new Knot(EnvType.CLIENT);
   var builtin=List.of(new GameProvider.BuiltinMod(List.of(),new BuiltinModMetadata.Builder("minecraft","26.3").build()),new GameProvider.BuiltinMod(List.of(),new BuiltinModMetadata.Builder("fabricloader","0.20.1").build()));
   var provider=(GameProvider)Proxy.newProxyInstance(SingleJarCheck.class.getClassLoader(),new Class[]{GameProvider.class},(proxy,method,a)->switch(method.getName()){
    case "getBuiltinMods"->builtin;case "getLaunchDirectory"->temp;case "getRawGameVersion","getNormalizedGameVersion"->"26.3";case "getGameId"->"minecraft";case "getGameName"->"Minecraft";case "toString"->"Headless metadata-only game context";default->throw new UnsupportedOperationException(method.getName());});
   var loader=FabricLoaderImpl.INSTANCE;loader.setGameProvider(provider);
   var discovery=new ModDiscoverer(new VersionOverrides(),new DependencyOverrides(temp.resolve("config")));discovery.addCandidateFinder(new DirectoryModCandidateFinder(mods,false));
   Map<String,Set<ModCandidateImpl>> disabled=new HashMap<>();var candidates=discovery.discoverMods(loader,disabled);var resolved=ModResolver.resolve(candidates,EnvType.CLIENT,disabled);Set<String> ids=new TreeSet<>();for(var candidate:resolved)ids.add(candidate.getId());
   for(String id:List.of("lunabuilder","fabric-api","fabric-api-base","fabric-lifecycle-events-v1","fabric-key-mapping-api-v1","fabric-screen-api-v1","fabric-events-interaction-v0"))if(!ids.contains(id))throw new AssertionError("Missing bundled dependency: "+id);
   if(candidates.stream().filter(c->!c.isBuiltin()&&c.isRoot()).count()!=1)throw new AssertionError("Test used more than one external mod JAR");
   System.out.println("PASS: Fabric Loader 0.20.1 discovers and resolves "+ids.size()+" mod/builtin IDs from exactly one external LunaBuilder JAR, including nested Fabric API modules. No game window or gameplay was launched.");
  }finally{try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
