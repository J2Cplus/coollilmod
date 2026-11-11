package dev.luna;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.*;
import net.minecraft.resources.Identifier;

/** Real client movement, creative inventory selection, and use-item interactions only. */
final class BuilderController {
 final LunaBuilder bridge;JsonObject task;long heartbeat,started;int ticks,attempts;String id="";Vec3 stand;
 BuilderController(LunaBuilder b){bridge=b;}
 void accept(JsonObject r){heartbeat=System.currentTimeMillis();JsonObject next=r.has("task")&&!r.get("task").isJsonNull()?r.getAsJsonObject("task"):null;if(next==null){task=null;id="";return;}String nextId=next.get("id").getAsString();if(!nextId.equals(id)){id=nextId;started=heartbeat;attempts=0;ticks=0;stand=null;}task=next;}
 void stop(Minecraft mc){task=null;id="";if(mc.player!=null){mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setShiftKeyDown(false);}}
 void fail(Minecraft mc,String message){bridge.botError=message;bridge.botTaskError=id;stop(mc);}
 @SuppressWarnings({"rawtypes","unchecked"}) static boolean matches(BlockState actual,String desired){String block=desired.split("\\[")[0];if(!BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(block))return false;if(!desired.contains("["))return true;for(String part:desired.substring(desired.indexOf('[')+1,desired.length()-1).split(",")){String[] kv=part.split("=");Property p=actual.getBlock().getStateDefinition().getProperty(kv[0]);if(p==null||!p.getName(actual.getValue(p)).equals(kv[1]))return false;}return true;}
 void tick(Minecraft mc){if(task==null||mc.player==null||mc.level==null||mc.gameMode==null)return;
  if(System.currentTimeMillis()-heartbeat>1200){stop(mc);return;}if(!mc.player.getAbilities().instabuild){if(System.currentTimeMillis()-started>12000)fail(mc,"Paired client did not receive scoped creative mode. Verify host connection and account name.");return;}
  if(System.currentTimeMillis()-started>45000){fail(mc,"Placement timed out: blocked flight path, unsupported state, or missing support");return;}
  BlockPos target=LunaBuilder.pos(task.getAsJsonArray("p"));String desired=task.get("state").getAsString();if(!mc.level.hasChunkAt(target))return;
  if(matches(mc.level.getBlockState(target),desired)){mc.player.setDeltaMovement(Vec3.ZERO);return;}
  mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();mc.player.setDeltaMovement(Vec3.ZERO);
  if(stand==null){stand=findStand(mc,target);if(stand==null){fail(mc,"No reachable flight position next to target");return;}}
  Vec3 delta=stand.subtract(mc.player.position());
  if(delta.length()>0.15){
   // First climb above obstacles, then approach. Collision is checked by normal entity movement.
   Vec3 dest=stand;double cruise=Math.min(mc.level.getMaxY()-2,Math.max(mc.player.getY(),target.getY()+3));
   if(Math.hypot(delta.x,delta.z)>3&&mc.player.getY()<cruise-0.3)dest=new Vec3(mc.player.getX(),cruise,mc.player.getZ());
   else if(Math.hypot(delta.x,delta.z)>1.5)dest=new Vec3(stand.x,Math.max(cruise,stand.y),stand.z);
   Vec3 step=dest.subtract(mc.player.position());if(step.length()>0.22)step=step.normalize().scale(0.22);face(mc,Vec3.atCenterOf(target));mc.player.move(MoverType.SELF,step);return;
  }
  if(++ticks%6!=0)return;
  var actual=mc.level.getBlockState(target);if(!actual.canBeReplaced()){
   // Destruction is allowed only at the exact task position whose prior state the host journaled.
   if(!matches(actual,task.get("before").getAsString())){fail(mc,"Target changed after inspection; stopping to protect player edits");return;}
   face(mc,Vec3.atCenterOf(target));mc.gameMode.startDestroyBlock(target,Direction.UP);mc.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);return;
  }
  if(desired.startsWith("minecraft:air")||desired.startsWith("minecraft:cave_air")||desired.startsWith("minecraft:void_air"))return;
  if(++attempts>12){fail(mc,"Cannot obtain requested block state through normal placement: "+desired);return;}
  String blockId=desired.split("\\[")[0];var block=BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));Item item=block.asItem();if(item==Items.AIR||!(item instanceof BlockItem blockItem)){fail(mc,"No placeable block item for "+blockId);return;}
  var stack=new ItemStack(item,64);mc.player.getInventory().setSelectedSlot(0);mc.player.getInventory().setItem(0,stack);mc.gameMode.handleCreativeModeItemAdd(stack,36);
  for(Direction offset:Direction.values()){
   BlockPos support=target.relative(offset);var supporting=mc.level.getBlockState(support);if(supporting.canBeReplaced()||supporting.hasBlockEntity()||!supporting.getFluidState().isEmpty())continue;Direction face=offset.getOpposite();
   for(double vertical:new double[]{0.5,0.25,0.75}){
    Vec3 hit=Vec3.atCenterOf(support).add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5);if(face.getAxis().isHorizontal())hit=new Vec3(hit.x,support.getY()+vertical,hit.z);
    if(mc.player.getEyePosition().distanceTo(hit)>4.5)continue;
    for(float yaw:new float[]{0,90,180,270}){mc.player.setYRot(yaw);mc.player.setXRot(65);var result=new BlockHitResult(hit,face,support,false);var ctx=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,stack,result);BlockState predicted=blockItem.getBlock().getStateForPlacement(ctx);if(predicted==null||!matches(predicted,desired))continue;
     // Facing values are sent through the normal movement packet before the next placement tick.
     if(!mc.player.connection.isAcceptingMessages())return;
     mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround(),mc.player.horizontalCollision));
     mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,result);mc.player.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);return;
    }
   }
  }
 }
 Vec3 findStand(Minecraft mc,BlockPos target){for(Direction d:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(int height:new int[]{1,2,0}){Vec3 p=Vec3.atCenterOf(target).add(d.getStepX()*2.2,height,d.getStepZ()*2.2);var box=mc.player.getBoundingBox().move(p.subtract(mc.player.position()));if(mc.level.noCollision(mc.player,box))return p;}return null;}
 void face(Minecraft mc,Vec3 point){Vec3 d=point.subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
}
