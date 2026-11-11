package dev.luna.tools;
import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import java.nio.file.*;
/** Exports the actual game registry for offline compiler compatibility checks. */
public final class RegistryExport {
 @SuppressWarnings({"rawtypes","unchecked"})
 public static void main(String[] args)throws Exception {
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();JsonObject registry=new JsonObject();
  for(var block:BuiltInRegistries.BLOCK){String id=BuiltInRegistries.BLOCK.getKey(block).toString();if(!id.startsWith("minecraft:"))continue;JsonObject d=new JsonObject(),props=new JsonObject();d.addProperty("blockEntity",block.defaultBlockState().hasBlockEntity());for(Property p:block.getStateDefinition().getProperties()){JsonArray values=new JsonArray();for(Object value:p.getPossibleValues())values.add(p.getName((Comparable)value));props.add(p.getName(),values);}d.add("properties",props);registry.add(id,d);}
  Path file=Path.of(args[0]);Files.createDirectories(file.getParent());Files.writeString(file,new Gson().toJson(registry));System.out.println("Exported "+registry.size()+" vanilla blocks to "+file);
 }
}
