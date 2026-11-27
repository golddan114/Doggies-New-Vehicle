package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Asset-derived launch offsets; gameplay does not infer them from a client-rendered model. */
public record NuclearModelData(double readyTailHeight,double rocketHeight,double rocketWidth) {
    public static final NuclearModelData DEFAULT=load();
    private static NuclearModelData load(){
        var stream=NuclearModelData.class.getResourceAsStream("/data/doggiesnewvehicle/nuclear/model.json");
        if(stream==null)throw new IllegalStateException("Missing nuclear model data");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            var j=JsonParser.parseReader(reader).getAsJsonObject();
            return new NuclearModelData(j.get("readyTailHeight").getAsDouble(),j.get("rocketHeight").getAsDouble(),j.get("rocketWidth").getAsDouble());
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    public NuclearModelData{
        if(!Double.isFinite(readyTailHeight)||readyTailHeight<0||!Double.isFinite(rocketHeight)||rocketHeight<=0
                ||!Double.isFinite(rocketWidth)||rocketWidth<=0)throw new IllegalArgumentException("Invalid nuclear model offsets");
    }
}
