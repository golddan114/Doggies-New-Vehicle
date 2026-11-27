package com.liucodex.doggiesnewvehicle.artillery.silo;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Progress belongs to the central impact record; missing progress migrates the previous queued format. */
public record NuclearPayloadProgress(boolean started,int terrainCursor,int fluidCursor,int fireCursor,
        int terrainRadius,int fluidRadius,boolean wave,boolean composite) {
    public static final NuclearPayloadProgress EMPTY=new NuclearPayloadProgress(false,0,0,0,
            NuclearPayloadRules.DEFAULT.terrainRadius(),NuclearPayloadRules.DEFAULT.fluidRadius(),true,true);
    public NuclearPayloadProgress(boolean started,int terrainCursor,int fluidCursor,int fireCursor,int terrainRadius,int fluidRadius,boolean wave){
        this(started,terrainCursor,fluidCursor,fireCursor,terrainRadius,fluidRadius,wave,false);
    }
    public NuclearPayloadProgress(boolean started,int terrainCursor,int fluidCursor){
        this(started,terrainCursor,fluidCursor,0,NuclearPayloadRules.DEFAULT.terrainRadius(),NuclearPayloadRules.DEFAULT.fluidRadius());
    }
    public NuclearPayloadProgress(boolean started,int terrainCursor,int fluidCursor,int fireCursor,int terrainRadius,int fluidRadius){
        this(started,terrainCursor,fluidCursor,fireCursor,terrainRadius,fluidRadius,false);
    }
    public NuclearPayloadProgress advance(int terrain,int fluid,int fire){
        return new NuclearPayloadProgress(true,terrain,fluid,fire,terrainRadius,fluidRadius,wave,composite);
    }
    int waveRadius(){return Math.max(terrainRadius,fluidRadius);}
    /** Normalized distance for both new waves and resumed spherical payloads. */
    public double distanceSquared(BlockPos offset,int radius){
        return composite?NuclearCraterShape.distance(offset,radius):wave?NuclearSphereScan.waveDistance(offset,radius)
                :((double)offset.getX()*offset.getX()+(double)offset.getY()*offset.getY()+(double)offset.getZ()*offset.getZ())/(radius*radius);
    }
    public NuclearPayloadProgress {
        if(composite&&!wave)throw new IllegalArgumentException("Composite scan requires wave ordering");
        if(terrainCursor<0||terrainCursor>(composite?NuclearCraterShape.size(Math.max(terrainRadius,fluidRadius))
                :wave?NuclearSphereScan.waveSize(Math.max(terrainRadius,fluidRadius)):NuclearSphereScan.size(terrainRadius))
                ||fluidCursor<0||fluidCursor>NuclearSphereScan.size(fluidRadius)
                ||fireCursor<0||fireCursor>NuclearSphereScan.size(terrainRadius)
                ||(wave&&(fluidCursor!=0||fireCursor!=0))
                ||(!started&&(terrainCursor!=0||fluidCursor!=0||fireCursor!=0)))throw new IllegalArgumentException("Invalid payload progress");
    }
    public boolean complete(){
        if(composite)return started&&terrainCursor==NuclearCraterShape.size(waveRadius());
        if(wave)return started&&terrainCursor==NuclearSphereScan.waveSize(waveRadius());
        return started&&terrainCursor==NuclearSphereScan.size(terrainRadius)&&fluidCursor==NuclearSphereScan.size(fluidRadius)
                &&fireCursor==NuclearSphereScan.size(terrainRadius);
    }
    public CompoundTag save(){
        CompoundTag t=new CompoundTag();t.putInt("Schema",composite?4:wave?3:2);t.putBoolean("Started",started);
        t.putInt("TerrainRadius",terrainRadius);t.putInt("FluidRadius",fluidRadius);
        t.putInt("Terrain",terrainCursor);t.putInt("Fluid",fluidCursor);t.putInt("Fire",fireCursor);return t;
    }
    public static NuclearPayloadProgress load(CompoundTag t){
        int schema=t.getInt("Schema");
        if(!t.contains("Schema",Tag.TAG_INT)||schema<1||schema>4||!t.contains("Started",Tag.TAG_BYTE)
                ||!t.contains("Terrain",Tag.TAG_INT)||!t.contains("Fluid",Tag.TAG_INT))throw new IllegalArgumentException("Unknown payload progress");
        if(schema==1){
            // Preserve the original cube indexing for already-started 1.1.48 cleanup jobs.
            return t.getBoolean("Started")?new NuclearPayloadProgress(true,t.getInt("Terrain"),t.getInt("Fluid"),NuclearSphereScan.size(8),8,10):EMPTY;
        }
        if(!t.contains("TerrainRadius",Tag.TAG_INT)||!t.contains("FluidRadius",Tag.TAG_INT)||schema>=3&&!t.contains("Fire",Tag.TAG_INT))
            throw new IllegalArgumentException("Missing payload scan fields");
        var loaded=new NuclearPayloadProgress(t.getBoolean("Started"),t.getInt("Terrain"),t.getInt("Fluid"),
                t.contains("Fire",Tag.TAG_INT)?t.getInt("Fire"):t.getBoolean("Started")?NuclearSphereScan.size(t.getInt("TerrainRadius")):0,
                t.getInt("TerrainRadius"),t.getInt("FluidRadius"),schema>=3,schema==4);
        // Only a started scan owns frozen indexing. Validate old fields before adopting current defaults.
        return loaded.started()?loaded:EMPTY;
    }
}
