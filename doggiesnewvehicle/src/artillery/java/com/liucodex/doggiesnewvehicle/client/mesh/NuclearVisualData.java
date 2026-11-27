package com.liucodex.doggiesnewvehicle.client.mesh;

import com.liucodex.doggiesnewvehicle.artillery.silo.NuclearMotion;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

/** Resource reload owns the immutable visual projection; malformed packs show no nuclear geometry. */
public record NuclearVisualData(NuclearMotion motion, List<Part> silo, List<Part> missile,
        List<Boolean> radar, List<Vector3f> nozzles, List<Batch> siloBase, List<Batch> siloGlow,
        List<Batch> missileBase, List<Batch> missileGlow) {
    public record Part(String name,int group,boolean rocket,ResourceLocation texture,ResourceLocation emission,boolean statusLamp,int tint,
            float lightX,float lightY,float lightZ) {
        public Part(String name,int group,boolean rocket,ResourceLocation texture,ResourceLocation emission,boolean statusLamp){
            this(name,group,rocket,texture,emission,statusLamp,0xffffff,0,0,0);
        }
    }
    public record Batch(ResourceLocation material,List<Part> parts) { }
    public NuclearVisualData(NuclearMotion motion,List<Part> silo,List<Part> missile,List<Boolean> radar,List<Vector3f> nozzles){
        this(motion,silo,missile,radar,nozzles,batches(silo,false),batches(silo,true),batches(missile,false),batches(missile,true));
    }
    /** One buffer request per material, rebuilt only by the existing resource reload owner. */
    static List<Batch> batches(List<Part> parts,boolean glow){
        var grouped=new java.util.LinkedHashMap<ResourceLocation,List<Part>>();
        for(var part:parts){var material=glow?part.emission():part.texture();
            if(material!=null)grouped.computeIfAbsent(material,key->new ArrayList<>()).add(part);}
        return grouped.entrySet().stream().map(e->new Batch(e.getKey(),List.copyOf(e.getValue()))).toList();
    }
    private static volatile NuclearVisualData cached;
    public static NuclearVisualData get(){
        var result=cached;if(result==null){synchronized(NuclearVisualData.class){
            if(cached==null)cached=load(Minecraft.getInstance().getResourceManager());result=cached;
        }}return result;
    }
    public static void install(NuclearVisualData data){cached=data;}
    public static NuclearVisualData load(ResourceManager resources){
        try(var binary=resources.open(id("meshes/nuclear_motion.nsm"));
                var reader=new InputStreamReader(resources.open(id("meshes/nuclear_parts.json")),StandardCharsets.UTF_8)){
            var motion=NuclearMotion.parse(binary.readAllBytes());var root=JsonParser.parseReader(reader).getAsJsonObject();
            List<Boolean> radar=new ArrayList<>();root.getAsJsonArray("groups").forEach(v->radar.add(v.getAsJsonObject().get("radar").getAsBoolean()));
            if(radar.size()!=motion.groupCount())throw new IllegalArgumentException("Motion group mismatch");
            List<Vector3f> ports=new ArrayList<>();root.getAsJsonArray("nozzles").forEach(v->{
                var a=v.getAsJsonArray();ports.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
            });
            if(ports.size()!=5||ports.stream().anyMatch(p->!p.isFinite()))throw new IllegalArgumentException("Invalid five-port layout");
            return new NuclearVisualData(motion,parts(root,"siloParts",motion.groupCount()),parts(root,"missileParts",motion.groupCount()),List.copyOf(radar),List.copyOf(ports));
        }catch(IOException|RuntimeException bad){
            com.mojang.logging.LogUtils.getLogger().error("Nuclear visual resource load failed",bad);
            return new NuclearVisualData(null,List.of(),List.of(),List.of(),List.of());
        }
    }
    private static List<Part> parts(com.google.gson.JsonObject root,String key,int groupCount){
        List<Part> result=new ArrayList<>();for(var value:root.getAsJsonArray(key)){var p=value.getAsJsonObject();int group=p.get("group").getAsInt();
            if(group<0||group>=groupCount||result.size()>=512)throw new IllegalArgumentException("Invalid part group");
            var probe=p.getAsJsonArray("lightProbe");
            if(probe!=null&&(probe.size()!=3||!Float.isFinite(probe.get(0).getAsFloat())
                    ||!Float.isFinite(probe.get(1).getAsFloat())||!Float.isFinite(probe.get(2).getAsFloat())))throw new IllegalArgumentException("Invalid light probe");
            result.add(new Part(p.get("name").getAsString(),group,p.get("rocket").getAsBoolean(),ResourceLocation.parse(p.get("texture").getAsString()),
                    p.get("emission").isJsonNull()?null:ResourceLocation.parse(p.get("emission").getAsString()),
                    p.has("statusLamp")&&p.get("statusLamp").getAsBoolean(),tint(p),
                    probe==null?0:probe.get(0).getAsFloat(),probe==null?0:probe.get(1).getAsFloat(),probe==null?0:probe.get(2).getAsFloat()));}
        return List.copyOf(result);
    }
    private static int tint(com.google.gson.JsonObject part){
        if(!part.has("tint"))return 0xffffff;
        String hex=part.get("tint").getAsString();
        if(!hex.matches("[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("Invalid nuclear paint");
        return Integer.parseInt(hex,16);
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle",path);}
}
