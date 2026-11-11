package dev.luna;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class BuilderScreen extends Screen {
 final LunaBuilder b;MultiLineEditBox prompt;EditBox style,code,custom,provider,model,speed;
 String promptText="",styleText="",dimensions="48,48,48",size="Medium",detail="Detailed";
 boolean prepare=false,interior=true;int left,content,page,libraryPage;JsonArray saved=new JsonArray();JsonObject settings=new JsonObject();
 BuilderScreen(LunaBuilder bridge){super(Component.literal("LunaBuilder"));b=bridge;promptText=b.draftPrompt;styleText=b.draftStyle;dimensions=b.draftDimensions;size=b.draftSize;detail=b.draftDetail;prepare=b.draftPrepare;interior=b.draftInterior;}
 void button(String label,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(label),btn->r.run()).bounds(x,y,w,20).build());}
 EditBox edit(String label,int x,int y,int w,String value){var e=new EditBox(font,x,y,w,20,Component.literal(label));e.setMaxLength(2048);e.setValue(value);e.setHint(Component.literal(label));addRenderableWidget(e);return e;}
 void api(String route,JsonObject body,java.util.function.Consumer<JsonObject> done){if(b.key.isEmpty()){b.error="Start local builder first.";return;}String key=b.key;b.request(route,body,key).whenComplete((r,e)->minecraft.execute(()->{if(!b.key.equals(key))return;if(e!=null)b.error=LunaBuilder.message(e);else{b.error="";done.accept(r);}}));}
 void selectPage(int next){page=next;rebuildWidgets();if(page==2)api("/plans",LunaBuilder.obj(),r->{saved=r.getAsJsonArray("plans");rebuildWidgets();});if(page==3)api("/settings",LunaBuilder.obj(),r->{settings=r;rebuildWidgets();});}
 @Override protected void init(){
  content=Math.min(width-20,720);left=(width-content)/2;
  if(!minecraft.hasSingleplayerServer()){
   code=edit("Host pairing code",left,60,Math.min(280,content),"");code.setMaxLength(12);button("Pair this builder client",left,86,Math.min(280,content),()->b.pairBot(code.getValue()));button("Close",left,112,100,this::onClose);return;
  }
  button(b.key.isEmpty()?"Start local builder":"Host connected",left,5,130,b::startHost);button("Close",left+content-60,5,60,this::onClose);
  String[] pages={"Build","Preview","Library","Settings"};int tab=content/4;for(int i=0;i<4;i++){final int n=i;button(pages[i],left+i*tab,31,tab-3,()->selectPage(n));}
  if(page==0){
   int y=59,h=Math.max(30,Math.min(110,height-208));prompt=MultiLineEditBox.builder().setX(left).setY(y).setPlaceholder(Component.literal("Describe the building, interior, and surroundings...")).build(font,content,h,Component.literal("Build prompt"));prompt.setCharacterLimit(12000);prompt.setValue(promptText);prompt.setValueListener(v->promptText=v);addRenderableWidget(prompt);y+=h+5;
   style=edit("Style, e.g. medieval timber",left,y,content,styleText);style.setResponder(v->styleText=v);y+=24;
   button("Size: "+size,left,y,content/2-2,()->{size=switch(size){case "Small"->"Medium";case "Medium"->"Large";case "Large"->"Custom";default->"Small";};dimensions=size.equals("Small")?"24,24,24":size.equals("Large")?"96,96,96":"48,48,48";rebuildWidgets();});button("Detail: "+detail,left+content/2+2,y,content/2-2,()->{detail=detail.equals("Detailed")?"Simple":"Detailed";rebuildWidgets();});y+=24;
   custom=edit("Max width,height,depth",left,y,content,dimensions);custom.setResponder(v->dimensions=v);y+=24;
   button("Terrain prep: "+(prepare?"on":"off"),left,y,content/2-2,()->{prepare=!prepare;rebuildWidgets();});button("Interior: "+(interior?"on":"off"),left+content/2+2,y,content/2-2,()->{interior=!interior;rebuildWidgets();});y+=24;
   button("Generate",left,y,content/2-2,()->generate(false));button("Offline demo",left+content/2+2,y,content/2-2,()->generate(true));
  }else if(page==1){
   int y=height-91,w=content/6;button("Rotate",left,y,w-3,()->b.action("rotate"));button("-X",left+w,y,w-3,()->move(-8,0));button("+X",left+w*2,y,w-3,()->move(8,0));button("-Z",left+w*3,y,w-3,()->move(0,-8));button("+Z",left+w*4,y,w-3,()->move(0,8));button("Aim site",left+w*5,y,w-3,this::aimSite);y+=24;w=content/5;
   button("Start",left,y,w-3,()->b.action("start"));button("Pause",left+w,y,w-3,()->b.action("pause"));button("Resume",left+w*2,y,w-3,()->b.action("resume"));button("Cancel",left+w*3,y,w-3,()->b.action("cancel"));button("Undo",left+w*4,y,w-3,()->b.action("undo"));
  }else if(page==2){
   int count=Math.max(1,(height-130)/24);for(int i=0;i<count;i++){int n=libraryPage*count+i;if(n>=saved.size())break;JsonObject p=saved.get(n).getAsJsonObject();button(LunaBuilder.str(p,"title","Saved plan"),left,60+i*24,content,()->{JsonObject x=LunaBuilder.obj();x.addProperty("id",p.get("id").getAsString());api("/load",x,r->{b.status=r;page=1;rebuildWidgets();});});}
   button("Previous",left,height-61,95,()->{libraryPage=Math.max(0,libraryPage-1);rebuildWidgets();});button("Next",left+100,height-61,95,()->{if((libraryPage+1)*count<saved.size())libraryPage++;rebuildWidgets();});
  }else{
   provider=edit("Full chat-completions endpoint (HTTPS)",left,73,content,LunaBuilder.str(settings,"providerUrl",""));model=edit("Exact provider API model ID",left,113,content,LunaBuilder.str(settings,"modelId",""));speed=edit("Placements per second, 1..10",left,153,Math.min(content,180),LunaBuilder.str(settings,"placementsPerSecond","4"));
   button("Save settings",left,179,110,()->{try{JsonObject values=LunaBuilder.obj();values.addProperty("providerUrl",provider.getValue().trim());values.addProperty("modelId",model.getValue().trim());values.addProperty("placementsPerSecond",Integer.parseInt(speed.getValue().trim()));JsonObject body=LunaBuilder.obj();body.add("update",values);api("/settings",body,r->{settings=r;b.error="Settings saved.";});}catch(Exception e){b.error="Placement speed must be an integer from 1 to 10.";}});
  }
 }
 void aimSite(){if(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit){JsonObject j=LunaBuilder.obj();j.addProperty("action","select");j.add("anchor",LunaBuilder.pos(hit.getBlockPos()));b.action(j);}else b.error="Aim at a ground block, then open LunaBuilder.";}
 void move(int x,int z){JsonObject j=LunaBuilder.obj();j.addProperty("action","move");j.addProperty("dx",x);j.addProperty("dz",z);b.action(j);}
 void generate(boolean demo){JsonObject j=LunaBuilder.obj();j.addProperty("action",demo?"demo":"generate");j.addProperty("prompt",prompt.getValue());j.addProperty("style",style.getValue());j.addProperty("detail",detail);j.addProperty("size",size);j.addProperty("prepare",prepare);j.addProperty("interior",interior);try{String[] ns=dimensions.split(",");if(ns.length!=3)throw new IllegalArgumentException();JsonArray a=new JsonArray();for(String s:ns){int n=Integer.parseInt(s.trim());if(n<4||n>96)throw new IllegalArgumentException();a.add(n);}j.add("dimensions",a);}catch(Exception e){b.error="Enter width,height,depth from 4 to 96.";return;}b.action(j);page=1;rebuildWidgets();}
 void preview(GuiGraphicsExtractor g,JsonObject j,int x,int y,int size){g.fill(x,y,x+size,y+size,0xff202020);if(j==null||!j.has("preview"))return;JsonArray blocks=j.getAsJsonArray("preview");int minX=128,minZ=128,maxX=-128,maxZ=-128;for(JsonElement el:blocks){JsonArray p=el.getAsJsonObject().getAsJsonArray("p");minX=Math.min(minX,p.get(0).getAsInt());maxX=Math.max(maxX,p.get(0).getAsInt());minZ=Math.min(minZ,p.get(2).getAsInt());maxZ=Math.max(maxZ,p.get(2).getAsInt());}double scale=(double)(size-8)/Math.max(1,Math.max(maxX-minX+1,maxZ-minZ+1));int rotation=j.get("rotation").getAsInt();for(JsonElement el:blocks){JsonObject block=el.getAsJsonObject();JsonArray p=block.getAsJsonArray("p");double px=(p.get(0).getAsInt()-minX)*scale,pz=(p.get(2).getAsInt()-minZ)*scale;for(int r=0;r<rotation;r++){double t=px;px=size-8-pz;pz=t;}int dx=x+4+(int)px,dz=y+4+(int)pz;int color=0xff808080|(block.get("state").getAsString().hashCode()&0x007f7f7f);g.fill(dx,dz,dx+Math.max(1,(int)Math.ceil(scale)),dz+Math.max(1,(int)Math.ceil(scale)),color);}}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);g.centeredText(font,"LunaBuilder",minecraft.hasSingleplayerServer()?left+content/2+35:width/2,11,0xffffffff);
  if(!minecraft.hasSingleplayerServer()){g.textWithWordWrap(font,Component.literal("Use a separate Fabric instance with a second Minecraft account. Enter the host's pairing code. This client connects to 127.0.0.1."),left,145,content,0xffdddddd);g.textWithWordWrap(font,Component.literal(b.error.isEmpty()?b.runtimeMessage:b.error),left,height-44,content,0xffdddddd);return;}
  JsonObject s=b.status,j=s.has("job")&&!s.get("job").isJsonNull()?s.getAsJsonObject("job"):null;
  if(page==1){int size=Math.max(50,Math.min(content/2-8,height-160));preview(g,j,left,59,size);int x=left+size+10,w=content-size-10,y=60;g.text(font,LunaBuilder.str(s,"phase","Connecting"),x,y,0xffffffff);y+=13;g.text(font,"Code: "+LunaBuilder.str(s,"pairCode","start host"),x,y,0xffdddddd);y+=13;g.text(font,"Bot: "+(s.has("botConnected")&&s.get("botConnected").getAsBoolean()?LunaBuilder.str(s,"botName","paired"):"waiting"),x,y,0xffdddddd);y+=13;if(j!=null){g.text(font,j.get("confirmed").getAsInt()+" / "+j.get("total").getAsInt()+" confirmed",x,y,0xffffffff);y+=13;if(j.has("origin")&&!j.get("origin").isJsonNull()){g.text(font,"Site: "+j.get("origin"),x,y,0xffdddddd);y+=13;}g.textWithWordWrap(font,Component.literal(LunaBuilder.str(j,"title","")),x,y,w,0xffffffff);}}
  else if(page==3){g.text(font,"Ollama / provider endpoint",left,60,0xffdddddd);g.text(font,"Exact API model identifier",left,100,0xffdddddd);g.text(font,"Placement speed",left,140,0xffdddddd);g.text(font,"API key: "+(settings.has("apiKeyRequired")&&!settings.get("apiKeyRequired").getAsBoolean()?"not needed":settings.has("apiKeyPresent")&&settings.get("apiKeyPresent").getAsBoolean()?"set":"unset"),left+content-100,140,0xffaaaaaa);}
  else if(page==2&&saved.isEmpty())g.text(font,"No saved plans in this world yet.",left,60,0xffdddddd);
  String message=b.error.isEmpty()?LunaBuilder.str(s,"message",b.runtimeMessage):b.error;g.textWithWordWrap(font,Component.literal(message),left,height-37,content,b.error.isEmpty()?0xffcccccc:0xffffaaaa);g.text(font,"Local LAN may also listen on other network interfaces.",left,height-12,0xff999999);
 }
 @Override public void removed(){b.draftPrompt=promptText;b.draftStyle=styleText;b.draftDimensions=dimensions;b.draftSize=size;b.draftDetail=detail;b.draftPrepare=prepare;b.draftInterior=interior;}
 @Override public void onClose(){if(minecraft.level==null)minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen());else super.onClose();}
 @Override public boolean isPauseScreen(){return false;}
}
