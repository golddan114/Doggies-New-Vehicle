package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The impact ledger is the only persisted owner; visual entities project this progress. */
public record VacuumPayloadProgress(boolean started, int age, int terrainCursor, boolean burst, long lastTick,
                                    int blastCursor, List<UUID> captured) {
    public static final int PULL_TICKS = 120;
    public static final int REVEAL_TICKS = 40;
    public static final int BURST_TICK = PULL_TICKS + REVEAL_TICKS;
    public static final int TOTAL_TICKS = 222;
    public static final int PULL_RADIUS = 32;
    public static final int EXECUTE_RADIUS = 12;
    public static final int TERRAIN_RADIUS = 16;
    public static final int MAX_CAPTURED = 512;
    public static final int BLAST_RADIUS = 16;
    public static final float BLAST_DAMAGE = 325F;
    public static final VacuumPayloadProgress EMPTY = new VacuumPayloadProgress(false, 0, 0, false, -1);

    public VacuumPayloadProgress(boolean started,int age,int terrainCursor,boolean burst,long lastTick) {
        this(started,age,terrainCursor,burst,lastTick,-1);
    }
    public VacuumPayloadProgress(boolean started,int age,int terrainCursor,boolean burst,long lastTick,int blastCursor) {
        this(started,age,terrainCursor,burst,lastTick,blastCursor,List.of());
    }

    public VacuumPayloadProgress {
        captured=List.copyOf(captured);
        if (age < 0 || age > TOTAL_TICKS || terrainCursor < 0
                || terrainCursor > VacuumTerrainScan.size() || lastTick < -1
                || blastCursor < -1 || blastCursor > VacuumTerrainScan.blastSize() || !burst && blastCursor != -1
                || !started && (age != 0 || terrainCursor != 0 || burst || lastTick != -1 || !captured.isEmpty())
                || captured.size()>MAX_CAPTURED || new LinkedHashSet<>(captured).size()!=captured.size()
                || started && lastTick < 0 || burst && age < PULL_TICKS) {
            throw new IllegalArgumentException("Invalid vacuum payload progress");
        }
    }

    public VacuumPayloadProgress start(long time) {
        return started ? this : new VacuumPayloadProgress(true, 0, 0, false, time);
    }

    public VacuumPayloadProgress tick(long time) {
        if (!started || time <= lastTick) return this;
        // No offline catch-up: unloading cannot skip the visible pull or replay a burst.
        return new VacuumPayloadProgress(true, Math.min(TOTAL_TICKS, age + 1), terrainCursor, burst, time, blastCursor,captured);
    }

    public VacuumPayloadProgress terrain(int cursor) {
        return new VacuumPayloadProgress(started, age, cursor, burst, lastTick, blastCursor,captured);
    }

    public VacuumPayloadProgress consumeBurst() {
        if (burst) return this;
        if (age < BURST_TICK) throw new IllegalStateException("Vacuum burst is not ready");
        return new VacuumPayloadProgress(started, age, terrainCursor, true, lastTick, blastCursor,captured);
    }

    public VacuumPayloadProgress blastTerrain(int cursor) {
        return new VacuumPayloadProgress(started,age,terrainCursor,burst,lastTick,cursor,captured);
    }

    public VacuumPayloadProgress capture(List<UUID> targets) {
        var merged=new LinkedHashSet<>(captured);merged.addAll(targets);
        if(merged.size()==captured.size())return this;
        if(!started||burst||age>=PULL_TICKS)throw new IllegalStateException("Vacuum capture phase ended");
        return new VacuumPayloadProgress(started,age,terrainCursor,burst,lastTick,blastCursor,List.copyOf(merged));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", 4); tag.putBoolean("Started", started); tag.putInt("Age", age);
        tag.putInt("Terrain", terrainCursor); tag.putInt("BlastTerrain", blastCursor); tag.putBoolean("Burst", burst); tag.putLong("LastTick", lastTick);
        var targets=new ListTag();for(UUID id:captured){var entry=new CompoundTag();entry.putUUID("Id",id);targets.add(entry);}
        tag.put("Captured",targets);
        return tag;
    }

    public static VacuumPayloadProgress load(CompoundTag tag) {
        if (!tag.contains("Schema", Tag.TAG_INT) || tag.getInt("Schema") < 1 || tag.getInt("Schema") > 4
                || !tag.contains("Started", Tag.TAG_BYTE) || !tag.contains("Age", Tag.TAG_INT)
                || !tag.contains("Terrain", Tag.TAG_INT) || !tag.contains("Burst", Tag.TAG_BYTE)
                || !tag.contains("LastTick", Tag.TAG_LONG)) throw new IllegalArgumentException("Unknown vacuum progress");
        int schema=tag.getInt("Schema"),oldCursor=tag.getInt("Terrain");
        int oldLimit=schema==1?NuclearSphereScan.size(16):schema==3?57777:VacuumTerrainScan.size();
        if(schema<4&&(oldCursor<0||oldCursor>oldLimit||!tag.getBoolean("Started")&&oldCursor!=0))
            throw new IllegalArgumentException("Invalid legacy vacuum cursor");
        if(schema>=3&&!tag.contains("BlastTerrain",Tag.TAG_INT))throw new IllegalArgumentException("Missing vacuum blast cursor");
        // Schema3's radius24 center-out scan shares this radius16 prefix; completed outer work needs no replay.
        int cursor=schema<3?0:schema==3?Math.min(oldCursor,VacuumTerrainScan.size()):oldCursor;
        int blastCursor=schema<3?(tag.getBoolean("Burst")?VacuumTerrainScan.blastSize():-1):tag.getInt("BlastTerrain");
        var targets=new java.util.ArrayList<UUID>();
        if(schema==4){
            if(!tag.contains("Captured",Tag.TAG_LIST))throw new IllegalArgumentException("Missing vacuum capture list");
            var list=tag.getList("Captured",Tag.TAG_COMPOUND);
            if(list.size()>MAX_CAPTURED||((ListTag)tag.get("Captured")).size()!=list.size())throw new IllegalArgumentException("Invalid vacuum capture list");
            for(Tag value:list){var entry=(CompoundTag)value;if(!entry.hasUUID("Id"))throw new IllegalArgumentException("Invalid vacuum target");targets.add(entry.getUUID("Id"));}
        }
        return new VacuumPayloadProgress(tag.getBoolean("Started"),tag.getInt("Age"),cursor,
                tag.getBoolean("Burst"),tag.getLong("LastTick"),blastCursor,targets);
    }
}
