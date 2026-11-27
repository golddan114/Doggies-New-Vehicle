package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.client.map.TacticalMapCache;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Seat-specific presentation. The carrier checks every action again on the server thread. */
final class CubeStationScreen extends Screen {
    private static final ResourceLocation PREVIEW = new ResourceLocation(CubePrototype.ID, "textures/gui/shanyue_aircraft.png");
    private static final int PANEL = 0xffc6c6c6;
    private static final int INK = 0xff323633;
    private static final int LIGHT = 0xfff3f2e9;
    private final CubeVehicle vehicle;
    private final int seat;
    private final boolean bridge;
    private final boolean pilot;
    private final ItemStack[] icons = new ItemStack[3];
    private int x, y, w, h, split, selected;
    private EditBox coordX, coordZ;
    private Button board, lower, navigate, scan, launch, lock;
    private double scale = .38;
    private boolean mapDragging, dragged, deckTab, filterOpen, follow=true;
    private double centerX,centerZ;
    private int listTop,filterTop;
    private long chosen=Long.MIN_VALUE;
    private final Button[] disband=new Button[2];
    private java.util.UUID confirmFlight;
    private int viewX=Integer.MIN_VALUE,viewZ,viewRadius;
    private Button filter,sort,prev,next,warp,auto,waypoint;
    private Integer selectedHeight;
    private java.util.List<String> radarTypes;

    static void open(Minecraft mc, CubeVehicle vehicle) {
        if (mc.player == null) return;
        int seat = vehicle.getSeatIndex(mc.player);
        if (seat >= 0 && seat < (vehicle.carrier() ? 2 : 1))
            mc.setScreen(new CubeStationScreen(vehicle, seat));
    }

    private static Component label(String name) { return Component.translatable("screen.dnvcarriercube." + name); }

    private CubeStationScreen(CubeVehicle vehicle, int seat) {
        super(label(!vehicle.carrier() ? "pilot" : seat == 0 ? "bridge" : "aviation"));
        this.vehicle = vehicle;
        this.seat = seat;
        bridge = vehicle.carrier() && seat == 0;
        pilot = !vehicle.carrier();
        radarTypes=bridge?readTypes():java.util.List.of();
        if (!pilot) {
            icons[0] = ContainerBlockItem.createInstance(CubePrototype.AIRCRAFT.get());
            icons[1] = icons[0];
            icons[2] = icons[1];
        }
    }

    @Override protected void init() {
        clearWidgets();
        w = Math.min(width - 8, 420);
        h = pilot ? 86 : Math.min(height - 8, 226);
        x = (width-w)/2;
        y = (height-h)/2;
        split = bridge && !deckTab ? x+w/2 : x+w/3;
        if (pilot) {
            board=button("exit",x+w/2-123,y+47,120,()->{
                var state=vehicle.bridgeView();
                if(!vehicle.onDeck())command(state.getBoolean("linked")?"capture":"join_carrier");
                else command("return_key");
            });
            launch=button("launch",x+w/2+3,y+47,120,()->command("pilot_launch"));
        } else if (bridge) {
            button("mapTab",x+w-145,y+3,65,()->tab(false));
            button("deckTab",x+w-78,y+3,70,()->tab(true));
            int start=split+8,avail=x+w-8-start,half=(avail-3)/2;
            if(deckTab) {
                for(int i=0;i<2;i++){
                    int slot=i;
                    disband[i]=button("disband",x+w-88,y+38+47*i,77,()->{
                        var info=vehicle.bridgeView().getCompound("deck");
                        if(!info.hasUUID("flight"+slot))return;
                        var id=info.getUUID("flight"+slot);
                        if(id.equals(confirmFlight)){command("disband "+id);confirmFlight=null;}
                        else confirmFlight=id;
                    });
                }
                button("manage",x+8,y+92,split-x-12,()->command("storage"));
                lock=button("lock",x+8,y+115,split-x-12,()->command("toggle_hover"));
                button("station",x+8,y+138,split-x-12,()->command("flight_station"));
                auto=button("manual",start,y+h-72,avail,()->command(vehicle.bridgeView().getBoolean("auto")?"auto_off":"auto_on"));
                launch=button("launch",start,y+h-50,half,()->command(interruptible(vehicle.bridgeView())?"abort":"launch"));
                lower=button("stow",start+half+3,y+h-50,half,()->command("lower_aircraft"));
            } else {
                filter=button("targets",start,y+26,avail,()->{radarTypes=readTypes();filterOpen=!filterOpen;filterTop=0;});
                sort=button("distance",start,y+46,half,()->page(filterId(),!grouped(),0));
                scan=button("scan",start+half+3,y+46,half,()->command("scan"));
                prev=button("previous",start,y+122,half,()->page(filterId(),grouped(),offset()-CubeRadar.PAGE));
                next=button("next",start+half+3,y+122,half,()->page(filterId(),grouped(),offset()+CubeRadar.PAGE));
                var saved=vehicle.bridgeView().contains("waypoint")?BlockPos.of(vehicle.bridgeView().getLong("waypoint")):vehicle.blockPosition();
                coordX=coordinate(start+8,y+146,half-8,coordX,saved.getX());
                coordZ=coordinate(start+half+11,y+146,half-8,coordZ,saved.getZ());
                waypoint=button("setWaypoint",start,y+163,avail,()->{
                    if(validCoordinates())command("waypoint "+coordinateArguments());
                });
                navigate=button("navigate",x+w-161,y+h-24,79,()->navigate(false));
                warp=button("warp",x+w-80,y+h-24,72,()->navigate(true));
            }
        } else {
            int start=split+7,half=(x+w-8-start)/2;
            board=button("board",start,y+h-49,half-2,()->command("board "+(selected+1)));
            lower=button("request",start+half,y+h-49,half,()->command("request_stow"));
        }

        refresh();
    }

    private Button button(String key,int left,int top,int width,Runnable action) {
        return addRenderableWidget(Button.builder(label(key), b -> action.run()).bounds(left,top,width,18).build());
    }

    private EditBox coordinate(int left,int top,int width,EditBox previous,int initial) {
        EditBox input = new EditBox(font,left,top,width,15,label("coordinates"));
        input.setMaxLength(9);
        input.setFilter(value -> value.matches("-?[0-9]*"));
        input.setValue(previous != null ? previous.getValue() : Integer.toString(initial));
        input.setResponder(value->{chosen=Long.MIN_VALUE;selectedHeight=null;});
        return addRenderableWidget(input);
    }

    private boolean validCoordinates() {
        try { return Math.abs((long)Integer.parseInt(coordX.getValue()))<=30000000
                && Math.abs((long)Integer.parseInt(coordZ.getValue()))<=30000000; }
        catch (NumberFormatException e) { return false; }
    }

    private void command(String action) {
        if (minecraft.player != null && minecraft.player.getVehicle() == vehicle && vehicle.getSeatIndex(minecraft.player) == seat)
            minecraft.player.connection.sendCommand("carriercube "+action);
    }

    private CompoundTag results(){return vehicle.bridgeView().getCompound("radarPage");}
    private ListTag rows(){return results().getList("rows",Tag.TAG_COMPOUND);}
    private String filterId(){return results().getString("filter");}
    private boolean grouped(){return results().getBoolean("group");}
    private int offset(){return results().getInt("offset");}
    private void page(String id,boolean grouped,int offset) {
        listTop=0;filterOpen=false;
        command("radar_page "+grouped+" "+Math.max(0,offset)+" "+(id.isEmpty()?"*":id));
    }
    private void tab(boolean deck){deckTab=deck;filterOpen=false;confirmFlight=null;viewX=Integer.MIN_VALUE;init();}
    private void navigate(boolean jump) {
        if(vehicle.bridgeView().getBoolean("cruise")){command("stop_cruise");return;}
        if(validCoordinates()){
            command((jump?"warp ":"cruise ")+coordinateArguments());
        }
    }
    private boolean interruptible(CompoundTag state){
        String phase=state.getString("phase");return phase.equals("COUNTDOWN") || phase.equals("LAUNCH");
    }
    private boolean freeFlight(CompoundTag state){return java.util.Set.of("CRUISE","STATION_KEEP").contains(state.getString("phase"));}
    private void refresh() {
        var state=vehicle.bridgeView();
        if(pilot) {
            int sec=state.getInt("countdown");
            boolean cancel=sec>0 || state.getBoolean("rolling");
            board.active=!vehicle.onDeck() || state.getInt("guidance")==1 || cancel || state.getBoolean("parked") && !state.getBoolean("cabinFull");
            board.setMessage(label(state.getInt("guidance")==1?"abortGuide":cancel?"abortLaunch":vehicle.onDeck()?"exit":state.getBoolean("linked")?"capture":"connect"));
            launch.visible=vehicle.onDeck();launch.active=state.getBoolean("parked") && state.getBoolean("soloReady") && !cancel;
        } else if(!bridge) {
            board.active=state.getBoolean("board") && (state.getInt("boardable") & 1<<selected)!=0;
            boolean solo=state.getBoolean("helmVacant");
            lower.active=state.getBoolean("lower") && (solo || !state.getBoolean("requested"));
            lower.setMessage(label(solo?"stow":state.getBoolean("requested")?"requested":"request"));
            board.setTooltip(board.active?null:net.minecraft.client.gui.components.Tooltip.create(label(freeFlight(state)?"awaitDeck":"boardHint")));
        } else if(deckTab) {
            var info=state.getCompound("deck");
            for(int i=0;i<2;i++){
                disband[i].visible=info.hasUUID("flight"+i);
                disband[i].setMessage(label(disband[i].visible && info.getUUID("flight"+i).equals(confirmFlight)?"confirmDisband":"disband"));
            }
            auto.setMessage(label(state.getBoolean("auto")?"automatic":"manual"));
            lower.active=state.getBoolean("lower");launch.active=state.getBoolean("launch") || interruptible(state);
            launch.setMessage(label(interruptible(state)?"abortLaunch":"launch"));
            lock.active=!state.getBoolean("cruise") && (freeFlight(state) || state.getBoolean("unlock"));
            lock.setMessage(label(freeFlight(state)?"lock":"unlock"));
            if (lock.active) lock.setTooltip(null);
            else {
                String reason = state.getBoolean("cruise") ? "navigation" : switch (state.getString("phase")) {
                    case "ALIGNING" -> "aligning";
                    case "READY" -> "occupied";
                    default -> "operation";
                };
                lock.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("message.dnvcarriercube.deck." + reason)));
            }
            lower.setTooltip(lower.active?null:net.minecraft.client.gui.components.Tooltip.create(label("stowHint")));
            launch.setTooltip(launch.active?null:net.minecraft.client.gui.components.Tooltip.create(label("launchHint")));
        } else {
            boolean active=state.getBoolean("cruise");
            boolean jumping=active && state.getBoolean("warp");
            navigate.setMessage(label(active && !jumping?"cancel":"navigate"));
            navigate.active=!jumping && (active || validCoordinates());
            warp.setMessage(label(jumping?"cancelWarp":"warp"));
            warp.active=jumping || !active && validCoordinates();
            waypoint.active=validCoordinates();
            scan.active=!state.getBoolean("scanning") && vehicle.level().getGameTime()>=state.getLong("radarAvailable");
            if(state.getBoolean("scanning")){
                int done=state.getInt("scanProgress"),total=state.getInt("scanTotal");
                String progress=total>0?Math.min(99,100L*done/total)+"%":Integer.toString(done);
                scan.setMessage(Component.literal(label("scanning").getString()+" "+progress));
            }else scan.setMessage(label("scan"));
            sort.setMessage(label(grouped()?"typeSort":"distance"));
            filter.setMessage(filterId().isEmpty()?label("all"):CubeRadarIcons.name(filterId()));
            int total=results().getInt("total");
            prev.active=offset()>0;next.active=offset()+CubeRadar.PAGE<total;
            prev.setMessage(Component.literal("‹ "+(total==0?0:offset()/CubeRadar.PAGE+1)+" / "+((total+CubeRadar.PAGE-1)/CubeRadar.PAGE)));
            next.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.dnvcarriercube.total",total)));
        }
    }

    @Override public void tick() {
        if (minecraft.player == null || !vehicle.isAlive() || minecraft.player.getVehicle()!=vehicle || vehicle.getSeatIndex(minecraft.player)!=seat) {
            onClose();
            return;
        }
        if (bridge && !deckTab) {
            if(follow){centerX=vehicle.getX();centerZ=vehicle.getZ();}
            coordX.tick();
            coordZ.tick();
            int radius=Math.min(1024,64+(int)Math.ceil(Math.max(w/2,h)/(2*scale)));
            int vx=(int)Math.round(centerX/64)*64,vz=(int)Math.round(centerZ/64)*64;
            if((viewX==Integer.MIN_VALUE || vehicle.level().getGameTime()%5==0) && (vx!=viewX || vz!=viewZ || radius!=viewRadius)){
                viewX=vx;viewZ=vz;viewRadius=radius;command("map_view "+vx+" "+vz+" "+radius);
            }
            var cache=TacticalMapCache.INSTANCE;
            cache.processPendingChunks(centerX,centerZ,512);
            cache.processChunkUpdates(vehicle.level(),vehicle.getX(),vehicle.getZ());
            cache.periodicRescan(vehicle.level(),vehicle.getX(),vehicle.getZ());
            cache.uploadDirtyTextures();
            cache.flushPendingDiskWritesIfNeeded();
        }
        refresh();
    }

    @Override public boolean isPauseScreen() { return false; }

    private static void bevel(GuiGraphics g,int x,int y,int w,int h,int fill,boolean inset) {
        g.fill(x,y,x+w,y+h,inset?0xff5f625e:LIGHT);
        g.fill(x+1,y+1,x+w,y+h,inset?LIGHT:0xff555955);
        g.fill(x+1,y+1,x+w-1,y+h-1,fill);
    }

    @Override public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);
        bevel(g,x,y,w,h,PANEL,false);
        g.drawString(font,title,x+9,y+8,INK,false);
        g.fill(x+7,y+22,x+w-7,y+23,0xff999d99);
        if (pilot) g.drawCenteredString(font,label(!vehicle.onDeck()?(vehicle.bridgeView().getBoolean("linked")?"approach":"independent"):vehicle.bridgeView().getBoolean("parked") && vehicle.bridgeView().getBoolean("cabinFull")?"cabinFull":board.active?"secured":"wait"),x+w/2,y+31,INK);
        else if (bridge && deckTab) drawDeck(g); else if (bridge) drawBridge(g); else drawAviation(g);
        if(!pilot) drawStatus(g);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        renderBackground(g);
        super.render(g,mouseX,mouseY,partial);
        if(bridge && deckTab && vehicle.bridgeView().getBoolean("trackBlocked"))
            g.drawString(font,label("blockedTrack"),split+9,y+128,INK,false);
        if(bridge && !deckTab && filterOpen) {
            g.flush();
            g.pose().pushPose();
            try {
                g.pose().translate(0, 0, 300);
                drawFilters(g);
                g.flush();
            } finally { g.pose().popPose(); }
        }
    }

    private java.util.List<String> types(){return radarTypes;}
    private java.util.List<String> readTypes() {
        return CubeRadar.filterTypes(vehicle.bridgeView());
    }

    private void drawAviation(GuiGraphics g) {
        var state=vehicle.bridgeView();
        g.drawString(font,label("storage"),x+8,y+29,INK,false);
        g.drawString(font,label("preview"),split+7,y+29,INK,false);
        for (int i=0;i<3;i++) {
            int top=y+43+i*31;
            boolean stocked=(state.getInt("stock") & (1<<i))!=0;
            bevel(g,x+7,top,split-x-9,29,i==selected?0xffabb7aa:0xffbdbfbb,true);
            if (stocked) g.renderItem(icons[i],x+10,top+6);
            g.drawString(font,Component.translatable("screen.dnvcarriercube.cell",i+1),x+29,top+4,INK,false);
            g.drawString(font,label(stocked?((state.getInt("eligible") & 1<<i)!=0?"stored":"incompatible"):cellInUse(state,i)?"deployed":"empty"),x+29,top+16,0xff4d5c4e,false);
        }
        int left=split+7, top=y+43, width=x+w-8-left, height=y+h-68-top;
        bevel(g,left,top,width,height,0xff323b42,true);
        if ((state.getInt("eligible") & 1<<selected)!=0 || cellInUse(state,selected)) {
            // The transparent preview is 760x520. Fit the aircraft's actual 580x422 silhouette.
            double fit=Math.min((width-4)/580.0,(height-4)/422.0);
            int imageW=(int)Math.round(580*fit), imageH=(int)Math.round(422*fit);
            g.blit(PREVIEW,left+(width-imageW)/2,top+(height-imageH)/2,imageW,imageH,119,0,580,422,760,520);
        } else g.drawCenteredString(font,label("reserve"),left+width/2,top+height/2-3,LIGHT);
        g.drawString(font,label("CRUISE".equals(state.getString("phase"))?"awaitDeck":"modes"),left,y+h-62,INK,false);
    }

    private boolean cellInUse(CompoundTag t,int cell){return t.getCompound("deck").getInt("cell0")==cell+1 || t.getCompound("deck").getInt("cell1")==cell+1;}
    private int statusColor(String status) {
        return switch(status){case "ready"->0xff67a273;case "flying"->0xffbc6455;case "lostContact"->0xff986c99;case "empty",""->0xff838782;default->0xffdebb65;};
    }
    private void lamp(GuiGraphics g,int sx,int sy,int slot,boolean full) {
        var t=vehicle.bridgeView();String status=t.getString("slot"+slot);
        bevel(g,sx,sy,7,7,statusColor(status),true);
        String value=label(status.isEmpty()?"empty":status).getString();
        if(status.equals("countdown"))value+=" "+t.getInt("seconds")+"s";
        String title=(slot+1)+" · "+value;
        int limit=full?x+w-sx-28:((bridge && !deckTab?w-177:w-18)/2)-11;
        g.drawString(font,font.plainSubstrByWidth(title,limit),sx+9,sy,INK,false);
    }
    private void drawStatus(GuiGraphics g) {
        int gap=bridge && !deckTab?(w-177)/2:(w-18)/2;
        lamp(g,x+8,y+h-15,0,false);lamp(g,x+8+gap,y+h-15,1,false);
    }
    private void drawDeck(GuiGraphics g) {
        var t=vehicle.bridgeView();
        g.drawString(font,label("storage"),x+9,y+34,INK,false);
        for(int i=0;i<3;i++){
            int sx=x+10+i*24;bevel(g,sx,y+53,22,22,0xff89908a,true);
            if((t.getInt("stock") & 1<<i)!=0)g.renderItem(icons[i],sx+3,y+56);
        }
        for(int i=0;i<2;i++) {
            int sy=y+35+47*i;bevel(g,split+8,sy,x+w-16-split,41,0xffb8bcb6,true);
            lamp(g,split+16,sy+10,i,true);
            var info=t.getCompound("deck");
            int cell=info.getInt("cell"+i);
            if(info.contains("point"+i)){
                var last=BlockPos.of(info.getLong("point"+i));
                g.drawString(font,last.getX()+", "+last.getY()+", "+last.getZ(),split+67,sy+25,INK,false);
            }
            if(cell!=0)g.drawString(font,Component.translatable("screen.dnvcarriercube.cell",cell),split+16,sy+25,INK,false);
        }
    }
    private void drawFilters(GuiGraphics g) {
        int left=split+8,top=y+44;
        var types=types();
        int visible=Math.min(8,types.size()+1-filterTop);
        bevel(g,left,top,x+w-8-left,visible*16+2,0xffbdc1b9,true);
        for(int i=0;i<visible;i++) {
            int n=filterTop+i;int sy=top+2+i*16;
            if(n==0)g.drawString(font,label("all"),left+5,sy+3,INK,false);
            else {String id=types.get(n-1);g.renderItem(CubeRadarIcons.icon(id),left+3,sy);g.drawString(font,CubeRadarIcons.name(id),left+23,sy+3,INK,false);}
        }
    }
    private void drawBridge(GuiGraphics g) {
        var state=vehicle.bridgeView();
        int left=x+8, top=y+28, right=split-1, bottom=y+h-46;
        int ctl=split+8;
        ListTag rows=rows();
        listTop=Math.max(0,Math.min(listTop,rows.size()-3));
        for(int i=0;i<3 && listTop+i<rows.size();i++) {
            CompoundTag row=rows.getCompound(listTop+i);int sy=y+67+i*18;
            g.fill(ctl,sy,x+w-8,sy+17,row.getLong("p")==chosen?0xffa9b9a5:0xffb7bbb5);
            g.renderItem(CubeRadarIcons.icon(row.getString("t")),ctl+1,sy);
            String distance=row.getInt("d")+"m";int dw=font.width(distance);
            String name=font.plainSubstrByWidth(CubeRadarIcons.name(row.getString("t")).getString(),x+w-ctl-35-dw);
            g.drawString(font,name,ctl+19,sy+5,INK,false);g.drawString(font,distance,x+w-12-dw,sy+5,INK,false);
        }
        if(rows.isEmpty())g.drawString(font,label(state.getBoolean("scanning")?"scanning":state.getBoolean("partial")?"partial":state.getBoolean("scanned")?"none":"unscanned"),ctl,y+76,INK,false);
        g.drawString(font,"X",coordX.getX()-8,y+150,INK,false);g.drawString(font,"Z",coordZ.getX()-8,y+150,INK,false);
        if(state.getBoolean("cruise")) {
            String stage=state.getString("nav");
            String nav=label(state.getBoolean("warp") && (stage.equals("PREPARE") || stage.equals("LOAD"))
                    ? "nav.warpSurvey" : "nav."+stage).getString();
            int sec=state.getInt("navSeconds");
            g.drawString(font,nav+(sec>0?" "+sec+"s":""),left,y+h-36,INK,false);
        } else if(validCoordinates())g.drawString(font,label("destination").getString()+" "+coordX.getValue()+", "+(selectedHeight==null?"":selectedHeight+", ")+coordZ.getValue(),left,y+h-36,INK,false);

        bevel(g,left-1,top-1,right-left+2,bottom-top+2,0xff283035,true);
        g.enableScissor(left,top,right,bottom);
        double cx=follow?vehicle.getX():centerX, cz=follow?vehicle.getZ():centerZ;
        int mx=(left+right)/2, my=(top+bottom)/2;
        var cache=TacticalMapCache.INSTANCE;
        int radius=(int)Math.ceil(Math.max(right-left,bottom-top)/(2*scale))+2;
        int tile=TacticalMapCache.TILE_SIZE;
        for (var p:cache.getVisibleTiles((int)Math.floor(cx),(int)Math.floor(cz),radius)) {
            var texture=cache.getTileTexture(p.getRx(),p.getRz());
            if (texture==null) continue;
            int tx=(int)Math.round(mx+(p.getRx()*tile-cx)*scale), tz=(int)Math.round(my+(p.getRz()*tile-cz)*scale);
            int endX=(int)Math.round(mx+((p.getRx()+1)*tile-cx)*scale), endZ=(int)Math.round(my+((p.getRz()+1)*tile-cz)*scale);
            g.blit(texture,tx,tz,endX-tx,endZ-tz,0,0,tile,tile,tile,tile);
        }
        mark(g,mx,my,cx,cz,vehicle.getX(),vehicle.getZ(),0xffeeeeee,"+");
        if (state.getBoolean("cruise")) mark(g,mx,my,cx,cz,state.getInt("x"),state.getInt("z"),0xffffd38b,"×");
        for(var tag:vehicle.mapMarkers()) {
            var row=(CompoundTag)tag;BlockPos p=BlockPos.of(row.getLong("p"));
            int px=(int)Math.round(mx+(p.getX()-cx)*scale),py=(int)Math.round(my+(p.getZ()-cz)*scale);
            if(px<left-8 || px>right+8 || py<top-8 || py>bottom+8)continue;
            if(row.getLong("p")==chosen)g.renderOutline(px-9,py-9,18,18,0xffffffa3);
            g.renderItem(CubeRadarIcons.icon(row.getString("t")),px-8,py-8);
        }
        if(validCoordinates())mark(g,mx,my,cx,cz,Integer.parseInt(coordX.getValue()),Integer.parseInt(coordZ.getValue()),0xffffd38b,"×");
        g.disableScissor();
        bevel(g,right-19,top+1,18,18,0xffa2aaa0,false);
        g.renderItem(net.minecraft.world.item.Items.COMPASS.getDefaultInstance(),right-18,top+2);
    }

    private void mark(GuiGraphics g,int mx,int my,double cx,double cz,double px,double pz,int color,String text) {
        int sx=(int)Math.round(mx+(px-cx)*scale), sy=(int)Math.round(my+(pz-cz)*scale);
        g.fill(sx-4,sy-5,sx+5,sy+5,0xff273035);
        g.drawCenteredString(font,text,sx,sy-4,color);
    }
    private boolean overMap(double px,double py) {
        return bridge && !deckTab && px>=x+8 && px<split && py>=y+28 && py<y+h-46;
    }
    private String coordinateArguments() {return Integer.parseInt(coordX.getValue())+" "+Integer.parseInt(coordZ.getValue());}
    private void selectHit(CompoundTag row,boolean center) {
        var pos=BlockPos.of(row.getLong("p"));
        coordX.setValue(Integer.toString(pos.getX()));coordZ.setValue(Integer.toString(pos.getZ()));
        chosen=pos.asLong();selectedHeight=CubeRadar.SLIME.equals(row.getString("t"))?null:pos.getY();
        if(center){follow=false;centerX=pos.getX();centerZ=pos.getZ();}
    }
    @Override public boolean mouseClicked(double px,double py,int button) {
        if(button==0 && bridge && !deckTab && filterOpen){
            int n=(int)((py-y-46)/16)+filterTop;
            if(px>=split+8 && px<x+w-8 && py>=y+46 && py<y+46+128 && n<=types().size()){
                page(n==0?"":types().get(n-1),grouped(),0);return true;
            }
            filterOpen=false;return true;
        }
        if(button==0 && bridge && !deckTab && px>=split+8 && px<x+w-8 && py>=y+67 && py<y+121) {
            int index=listTop+(int)(py-y-67)/18;if(index<rows().size())selectHit(rows().getCompound(index),true);
            return true;
        }
        if (button==0 && !bridge && !pilot && px>=x+7 && px<split-2 && py>=y+43 && py<y+136) {
            selected=(int)(py-y-43)/31;refresh();return true;
        }
        if(button==0 && overMap(px,py)) {
            if(px>=split-20 && py<y+48){follow=true;centerX=vehicle.getX();centerZ=vehicle.getZ();return true;}
            if(follow){centerX=vehicle.getX();centerZ=vehicle.getZ();}
            setFocused(null);mapDragging=true;dragged=false;return true;
        }
        return super.mouseClicked(px,py,button);
    }
    @Override public boolean mouseDragged(double px,double py,int button,double dx,double dy) {
        if(mapDragging && button==0){follow=false;dragged=true;centerX-=dx/scale;centerZ-=dy/scale;return true;}
        return super.mouseDragged(px,py,button,dx,dy);
    }
    @Override public boolean mouseReleased(double px,double py,int button) {
        if(mapDragging && !dragged && overMap(px,py)) {
            int mx=(x+8+split-1)/2,my=(y+28+y+h-46)/2;
            double wx=centerX+(px-mx)/scale,wz=centerZ+(py-my)/scale;
            CompoundTag closest=null;double best=9/scale;
            for(var tag:vehicle.mapMarkers()){var row=(CompoundTag)tag;var p=BlockPos.of(row.getLong("p"));
                double d=Math.hypot(wx-p.getX(),wz-p.getZ());if(d<=best){best=d;closest=row;}}
            if(closest!=null)selectHit(closest,false);
            else{chosen=Long.MIN_VALUE;coordX.setValue(Integer.toString(Mth.floor(wx)));coordZ.setValue(Integer.toString(Mth.floor(wz)));}
        }
        mapDragging=false;return super.mouseReleased(px,py,button);
    }

    @Override public boolean mouseScrolled(double px,double py,double change) {
        if(bridge && !deckTab && px>=split+8 && px<x+w-8) {
            if(filterOpen){filterTop=Mth.clamp(filterTop-(int)Math.signum(change),0,Math.max(0,types().size()-7));return true;}
            if(py>=y+67 && py<y+121){listTop=Mth.clamp(listTop-(int)Math.signum(change),0,Math.max(0,rows().size()-3));return true;}
        }
        if (!overMap(px,py)) return super.mouseScrolled(px,py,change);
        scale=Mth.clamp(scale*Math.pow(1.25,change),.15,2.0);
        return true;
    }
}
