package com.liucodex.doggiesnewvehicle.client.walker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

/** Reload-owned immutable upstream block-model matrices; no BlockDisplay entities or per-frame parsing. */
public final class HexbotModel extends SimplePreparableReloadListener<HexbotModel.Data> {
    public record Piece(BlockState block,Matrix4f matrix,boolean emissive){ }
    public record Data(List<Piece> torso,List<List<Piece>> legs){ }
    private static volatile Data current;
    public static Data current(){return current;}
    @Override protected Data prepare(ResourceManager resources,ProfilerFiller profiler){
        ResourceLocation location=ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","models/hexbot.json");
        try(var reader=new InputStreamReader(resources.getResourceOrThrow(location).open(),StandardCharsets.UTF_8)){
            JsonObject obj=JsonParser.parseReader(reader).getAsJsonObject();
            if(obj.get("schema").getAsInt()!=1)throw new IllegalArgumentException("HexBot schema");
            List<List<Piece>> legs=new ArrayList<>();
            for(String name:new String[]{"base","femur","tibia","tip"})legs.add(pieces(obj.getAsJsonObject("segments").getAsJsonArray(name)));
            return new Data(pieces(obj.getAsJsonArray("torso")),List.copyOf(legs));
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load HexBot model",e);}
    }
    @Override protected void apply(Data data,ResourceManager resources,ProfilerFiller profiler){current=data;}
    private static List<Piece> pieces(JsonArray array){
        if(array.size()>64)throw new IllegalArgumentException("HexBot piece budget");
        List<Piece> result=new ArrayList<>();
        for(var value:array){
            JsonObject obj=value.getAsJsonObject();float[] a=new float[16];JsonArray raw=obj.getAsJsonArray("matrix");
            if(raw.size()!=16)throw new IllegalArgumentException("HexBot matrix size");
            for(int i=0;i<16;i++){a[i]=raw.get(i).getAsFloat();if(!Float.isFinite(a[i])||Math.abs(a[i])>8)throw new IllegalArgumentException("HexBot matrix range");}
            Matrix4f matrix=new Matrix4f(a[0],a[4],a[8],a[12],a[1],a[5],a[9],a[13],a[2],a[6],a[10],a[14],a[3],a[7],a[11],a[15]);
            result.add(new Piece(state(obj.get("block").getAsString()),matrix,obj.get("emissive").getAsBoolean()));
        }
        return List.copyOf(result);
    }
    private static BlockState state(String text){
        int split=text.indexOf('[');String id=split<0?text:text.substring(0,split);
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("HexBot block "+id);
        BlockState state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(split>=0)for(String entry:text.substring(split+1,text.length()-1).split(",")){
            String[] pair=entry.split("=");Property<?> property=state.getBlock().getStateDefinition().getProperty(pair[0]);
            if(property==null||pair.length!=2)throw new IllegalArgumentException("HexBot property");
            state=property(state,property,pair[1]);
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow());
    }
}
