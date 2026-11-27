package com.liucodex.carriercube;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class CubeRadar {
    static final String SLIME="dnvcarriercube:slime_chunk", PORTAL="dnvcarriercube:portal_room";
    static final int PAGE=24, RADIUS=16;
    record Hit(String type,BlockPos pos) {}
    private record Read(ChunkPos at,CollectFields fields,CompletableFuture<Void> done) {}
    private final BlockPos origin;
    private final ChunkPos central;
    private final LinkedHashSet<Hit> hits=new LinkedHashSet<>();
    private final Set<Long> live=new HashSet<>();
    private final ArrayDeque<Read> pending=new ArrayDeque<>();
    private final CompletableFuture<CubeSavedRegions> directory;
    private final List<String> types;
    private int cursor;
    private List<Hit> byDistance=List.of(),byType=List.of();
    private record Index(List<Hit> distance,List<Hit> grouped,Map<String,List<Hit>> categories,Map<Long,List<Hit>> tiles) {}
    private Map<Long,List<Hit>> tiles=Map.of();
    private int visited,total;
    int total(){return total;}
    int progress(){return cursor+visited-pending.size();}
    private static long tile(int x,int z){return ChunkPos.asLong(x,z);}
    ListTag markers(String type,int x,int z,int radius) {
        var result=new ListTag();
        for(int bx=(x-radius)>>7;bx<=(x+radius)>>7;bx++)
            for(int bz=(z-radius)>>7;bz<=(z+radius)>>7;bz++)
                for(var hit:tiles.getOrDefault(tile(bx,bz),List.of())) {
                    if((!type.isEmpty() && !type.equals(hit.type)) || Math.abs(hit.pos.getX()-x)>radius || Math.abs(hit.pos.getZ()-z)>radius)continue;
                    var row=new CompoundTag();row.putString("t",hit.type);row.putLong("p",hit.pos.asLong());result.add(row);
                }
        return result;
    }
    private CompletableFuture<Index> indexing;
    private Map<String,List<Hit>> categories=Map.of();
    private boolean failed;

    CubeRadar(ServerLevel level, BlockPos pos) {
        origin=pos.immutable();central=new ChunkPos(pos);
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var ids=new ArrayList<>(registry.keySet().stream().map(Object::toString).sorted().toList());
        ids.add(SLIME);ids.add(PORTAL);types=List.copyOf(ids);
        var root=level.getServer().getWorldPath(LevelResource.ROOT);
        var region=DimensionType.getStorageFolder(level.dimension(),root).resolve("region");
        directory=CompletableFuture.supplyAsync(()->CubeSavedRegions.open(region,central),net.minecraft.Util.ioPool());
    }
    static List<String> filterTypes(CompoundTag view) {
        var types=new ArrayList<String>();
        view.getList("structureTypes",Tag.TAG_STRING).forEach(id->types.add(id.getAsString()));
        types.add(SLIME);types.add(PORTAL);
        return List.copyOf(types);
    }

    static ListTag structureTypes(ServerLevel world) {
        var ids=new ListTag();
        world.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .sorted().forEach(id->ids.add(StringTag.valueOf(id.toString())));
        return ids;
    }
    List<String> types() {return types;}
    boolean failed(){return failed;}
    private void offer(String type,BlockPos pos){hits.add(new Hit(type,pos.immutable()));}
    private double distance(BlockPos p){return Math.hypot((double)p.getX()-origin.getX(),(double)p.getZ()-origin.getZ());}
    private void slime(ServerLevel level,ChunkPos at) {
        if(level.dimension()==Level.OVERWORLD && WorldgenRandom.seedSlimeChunk(at.x,at.z,level.getSeed(),987234911L).nextInt(10)==0)
            offer(SLIME,new BlockPos(at.getMiddleBlockX(),origin.getY(),at.getMiddleBlockZ()));
    }
    private void loaded(ServerLevel level,LevelChunk chunk) {
        var at=chunk.getPos();
        if(!live.add(at.toLong()))return;
        slime(level,at);
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(var entry:chunk.getAllStarts().entrySet()) {
            var start=entry.getValue();
            var id=registry.getKey(entry.getKey());
            if(id!=null && start.isValid()) {
                offer(id.toString(),start.getBoundingBox().getCenter());
                if(id.toString().equals("minecraft:stronghold"))for(var p:start.getPieces())
                    if(p instanceof net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.PortalRoom && p.getOrientation()!=null) {
                        var box=p.getBoundingBox();
                        portalPiece(new int[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()},p.getOrientation().get2DDataValue());
                    }
            }
        }
        if(level.dimension()==Level.OVERWORLD)scanPortalFrames(level,chunk);
    }

    boolean step(ServerLevel level) {
        if(indexing!=null) {
            if(!indexing.isDone())return false;
            var index=indexing.join();byDistance=index.distance;byType=index.grouped;categories=index.categories;tiles=index.tiles;
            return true;
        }
        try {
            for(int i=0;i<8 && cursor<1089;i++,cursor++) {
                var chunk=level.getChunkSource().getChunkNow(central.x+cursor%33-RADIUS,central.z+cursor/33-RADIUS);
                if(chunk!=null)loaded(level,chunk);
            }
            if(cursor<1089 || !directory.isDone())return false;
            var saved=directory.join();total=1089+saved.total();
            for(int budget=0;budget<24 && !pending.isEmpty() && pending.peek().done.isDone();budget++) {
                var read=pending.remove();read.done.join();
                if(read.fields.getResult() instanceof CompoundTag nbt) {
                    slime(level,read.at);
                    CompoundTag starts=nbt.getCompound("structures").getCompound("starts");
                    if(starts.isEmpty())starts=nbt.getCompound("Level").getCompound("Structures").getCompound("Starts");
                    parseStarts(starts,read.at);
                }
            }
            for(int budget=0;budget<24 && pending.size()<24 && saved.hasNext();budget++) {
                var at=saved.next();visited++;
                if(live.contains(at.toLong()))continue;
                var chunk=level.getChunkSource().getChunkNow(at.x,at.z);
                if(chunk!=null){loaded(level,chunk);continue;}
                var fields=new CollectFields(new FieldSelector("structures",CompoundTag.TYPE,"starts"),
                        new FieldSelector("Level","Structures",CompoundTag.TYPE,"Starts"),new FieldSelector(IntTag.TYPE,"DataVersion"));
                pending.add(new Read(at,fields,level.getChunkSource().chunkScanner().scanChunk(at,fields)));
            }
            if(saved.hasNext() || !pending.isEmpty())return false;
        } catch(java.util.concurrent.CompletionException error) {
            failed=true;
            com.mojang.logging.LogUtils.getLogger().warn("Carrier radar could not finish reading saved regions",error);
        }
        var nearest=Comparator.comparingDouble((Hit h)->distance(h.pos)).thenComparing(Hit::type).thenComparingLong(h->h.pos.asLong());
        var snapshot=List.copyOf(hits);hits.clear();live.clear();
        indexing=CompletableFuture.supplyAsync(()->{
            var distance=snapshot.stream().sorted(nearest).toList();
            var groups=new TreeMap<String,List<Hit>>();
            for(var hit:distance)groups.computeIfAbsent(hit.type,k->new ArrayList<>()).add(hit);
            var spatial=new HashMap<Long,List<Hit>>();
            for(var hit:distance)spatial.computeIfAbsent(tile(hit.pos.getX()>>7,hit.pos.getZ()>>7),k->new ArrayList<>()).add(hit);
            return new Index(distance,groups.values().stream().flatMap(List::stream).toList(),groups,spatial);
        },net.minecraft.Util.backgroundExecutor());
        return false;
    }

    private void parseStarts(CompoundTag starts,ChunkPos at) {
        for(String key:starts.getAllKeys()) {
            CompoundTag start=starts.getCompound(key);
            String id=start.getString("id");
            if(id.isEmpty() || id.equalsIgnoreCase("INVALID"))continue;
            if(!id.contains(":"))id="minecraft:"+id.toLowerCase(Locale.ROOT);
            var children=start.getList("Children",Tag.TAG_COMPOUND);
            int[] total=null;
            for(var child:children) {
                CompoundTag part=(CompoundTag)child;int[] box=part.getIntArray("BB");
                if(box.length!=6)continue;
                if(total==null)total=box.clone();
                else for(int i=0;i<3;i++){total[i]=Math.min(total[i],box[i]);total[i+3]=Math.max(total[i+3],box[i+3]);}
                if(part.getString("id").toLowerCase(Locale.ROOT).matches("(minecraft:)?shpr"))portalPiece(box,part.getInt("O"));
            }
            int[] bounds=start.getIntArray("BB");
            if(bounds.length==6)total=bounds;
            offer(id,total==null?new BlockPos(at.getMiddleBlockX(),origin.getY(),at.getMiddleBlockZ()):
                    new BlockPos((total[0]+total[3]+1)>>1,(total[1]+total[4]+1)>>1,(total[2]+total[5]+1)>>1));
        }
    }
    private void portalPiece(int[] b,int orientation) {
        if(orientation<0 || orientation>3)return;
        int x=b[0]+5,z=b[2]+10;
        switch(Direction.from2DDataValue(orientation)){
            case NORTH -> z=b[5]-10;
            case EAST -> {x=b[0]+10;z=b[2]+5;}
            case WEST -> {x=b[3]-10;z=b[2]+5;}
        }
        offer(PORTAL,new BlockPos(x,b[1]+3,z));
    }

    CompoundTag page(String type,boolean grouped,int offset) {
        List<Hit> rows=grouped?byType:byDistance;
        var selected=type.isEmpty()?rows:categories.getOrDefault(type,List.of());
        int start=Math.max(0,Math.min(offset,Math.max(0,selected.size()-1)/PAGE*PAGE));
        var page=new CompoundTag();var list=new ListTag();
        for(var hit:selected.subList(start,Math.min(start+PAGE,selected.size()))) {
            var row=new CompoundTag();row.putString("t",hit.type);row.putLong("p",hit.pos.asLong());
            row.putInt("d",(int)Math.round(distance(hit.pos)));list.add(row);
        }
        page.put("rows",list);page.putInt("total",selected.size());page.putInt("offset",start);page.putString("filter",type);page.putBoolean("group",grouped);
        return page;
    }
    private void scanPortalFrames(ServerLevel level, LevelChunk chunk) {
        // The north middle frame points south to the room center. Section palettes skip ordinary terrain.
        var sections=chunk.getSections();
        for (int i=0; i<sections.length; i++) {
            var section=sections[i];
            if (!section.maybeHas(state->state.is(Blocks.END_PORTAL_FRAME))) continue;
            int baseY=chunk.getSectionYFromSectionIndex(i)<<4;
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                var state=section.getBlockState(x,y,z);
                if (!faces(state,Direction.SOUTH)) continue;
                BlockPos center=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z+2);
                if (completeRing(level,center)) offer(PORTAL,center);
            }
        }
    }

    private static boolean faces(BlockState state,Direction facing) {
        return state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.FACING)==facing;
    }

    private static boolean completeRing(ServerLevel level,BlockPos center) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos middle=center.relative(side,2);
            for (int offset=-1;offset<=1;offset++) {
                BlockPos at=middle.relative(side.getClockWise(),offset);
                var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);
                if (chunk==null || !faces(chunk.getBlockState(at),side.getOpposite())) return false;
            }
        }
        return true;
    }
}
