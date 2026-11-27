package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.UUID;

/** Three storage cells, two persistent flight numbers and one outstanding aircraft per cell. */
final class CubeHangar {
    enum Bay {
        ONE("PILOTED"), TWO("COMBAT_DRONE"), THREE("UTILITY_DRONE");
        final String oldName;
        Bay(String oldName) { this.oldName=oldName; }
    }
    private final EnumMap<Bay,ItemStack> boxes=new EnumMap<>(Bay.class);
    private final EnumMap<Bay,UUID> reservations=new EnumMap<>(Bay.class);
    private final EnumMap<Bay,Long> lastPoint=new EnumMap<>(Bay.class);
    Long lastPoint(Bay bay){return lastPoint.get(bay);}
    void seen(Bay bay,long point){lastPoint.put(bay,point);}
    Bay returnBay(){
        if(freeSlot()<0)return null;
        for(var bay:Bay.values())if(!reserved(bay) && !boxes.containsKey(bay))return bay;
        return null;
    }
    boolean enroll(UUID id){
        var bay=returnBay();
        if(bay==null || source(id)!=null)return false;
        slots[freeSlot()]=bay;reservations.put(bay,id);return true;
    }
    private final Bay[] slots=new Bay[2];

    CubeHangar() {
        boxes.put(Bay.ONE,ContainerBlockItem.createInstance(CubePrototype.AIRCRAFT.get()));
        boxes.put(Bay.TWO,ContainerBlockItem.createInstance(CubePrototype.AIRCRAFT.get()));
    }
    static boolean allowed(ItemStack stack) {
        if (!(stack.getItem() instanceof ContainerBlockItem)) return false;
        CompoundTag nbt=BlockItem.getBlockEntityData(stack);
        if (nbt==null || !(CubePrototype.ID+":aircraft").equals(nbt.getString("EntityType"))) return false;
        CompoundTag stored=nbt.getCompound("Entity");
        return !stored.contains("id") || (CubePrototype.ID+":aircraft").equals(stored.getString("id"));
    }
    boolean setBox(Bay bay,ItemStack stack) {
        if (reserved(bay) || !stack.isEmpty() && (stack.getCount()!=1 || !allowed(stack))) return false;
        if (stack.isEmpty()) boxes.remove(bay); else boxes.put(bay,stack.copy());
        return true;
    }
    boolean reserved(Bay bay) { return reservations.containsKey(bay); }
    int freeSlot() { for(int i=0;i<slots.length;i++) if(slots[i]==null)return i; return -1; }
    Bay bayAt(int slot) { return slots[slot]; }
    UUID identity(Bay bay) { return bay==null?null:reservations.get(bay); }
    Bay source(UUID id) {
        if(id!=null) for(var entry:reservations.entrySet()) if(id.equals(entry.getValue())) return entry.getKey();
        return null;
    }
    int stockMask() { int mask=0;for(Bay b:boxes.keySet())mask|=1<<b.ordinal();return mask; }
    int eligibleMask() { int mask=0;for (Bay b:boxes.keySet()) if(!reserved(b) && allowed(boxes.get(b))) mask|=1<<b.ordinal();return mask; }
    ItemStack box(Bay bay) { return boxes.getOrDefault(bay,ItemStack.EMPTY).copy(); }
    boolean reserve(Bay bay, UUID aircraft) {
        int slot=freeSlot();
        if (slot<0 || reserved(bay) || source(aircraft)!=null || !allowed(box(bay))) return false;
        reservations.put(bay,aircraft);slots[slot]=bay;return true;
    }
    private void release(Bay bay) {
        reservations.remove(bay);lastPoint.remove(bay);
        for(int i=0;i<slots.length;i++) if(slots[i]==bay)slots[i]=null;
    }
    void cancel(Bay bay,UUID id) { if(boxes.containsKey(bay) && id.equals(identity(bay)))release(bay); }
    void checkout(Bay bay,UUID id) {
        if(!id.equals(identity(bay)) || boxes.remove(bay)==null) throw new IllegalStateException("Unreserved crate");
    }
    boolean inFlight(Bay bay,UUID id) {return bay!=null && id.equals(identity(bay)) && !boxes.containsKey(bay);}
    void stow(Bay bay,UUID id,ItemStack box) {
        if (!allowed(box) || !inFlight(bay,id)) throw new IllegalStateException("Unmatched aircraft");
        boxes.put(bay,box);release(bay);
    }
    void lost(UUID id) { Bay bay=source(id); if (inFlight(bay,id)) release(bay); }
    CompoundTag save() {
        CompoundTag tag=new CompoundTag();
        for(Bay bay:Bay.values()) {
            CompoundTag row=new CompoundTag();
            if(boxes.containsKey(bay))row.put("box",boxes.get(bay).save(new CompoundTag()));
            if(lastPoint.containsKey(bay))row.putLong("point",lastPoint.get(bay));
            if(reserved(bay)) {row.putUUID("deployed",identity(bay));row.putInt("slot",slots[0]==bay?0:1);}
            tag.put(bay.name(),row);
        }
        return tag;
    }
    void restore(CompoundTag tag) {
        boxes.clear();reservations.clear();lastPoint.clear();java.util.Arrays.fill(slots,null);
        for(Bay bay:Bay.values()) {
            CompoundTag row=tag.getCompound(tag.contains(bay.name())?bay.name():bay.oldName);
            ItemStack stack=ItemStack.of(row.getCompound("box"));
            if(!stack.isEmpty())boxes.put(bay,stack);
            int slot=row.contains("slot")?row.getInt("slot"):freeSlot();
            if(row.hasUUID("deployed") && slot>=0 && slot<2 && slots[slot]==null) {
                if(row.contains("point"))lastPoint.put(bay,row.getLong("point"));
                reservations.put(bay,row.getUUID("deployed"));slots[slot]=bay;
            }
        }
    }
}
