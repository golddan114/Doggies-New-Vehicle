package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension registration, launch receipts and ticket recovery; malformed data remains read-only. */
public final class NuclearSiloStore extends SavedData {
    public static final String DATA_NAME = "doggiesnewvehicle_nuclear_silo_v1";
    public static final int MAX_FLIGHTS = 64;
    private static final int MAX_PENDING = MAX_FLIGHTS + 1;
    public record Flight(UUID id, UUID actor, UUID silo, long expires, BlockPos lastPosition, boolean committed, SiloPayloadKind kind) {
        public Flight(UUID id,UUID actor,UUID silo,long expires,BlockPos lastPosition,boolean committed) {
            this(id,actor,silo,expires,lastPosition,committed,SiloPayloadKind.NUCLEAR);
        }
        public Flight {
            if(id==null||actor==null||silo==null||lastPosition==null||expires<0||kind==null)throw new IllegalArgumentException("Invalid flight record");
            lastPosition=lastPosition.immutable();
        }
    }
    public record Impact(UUID id, UUID actor, BlockPos position, NuclearPayloadProgress progress,
                         SiloPayloadKind kind, VacuumPayloadProgress vacuum) {
        public Impact(UUID id,UUID actor,BlockPos position,NuclearPayloadProgress progress) {
            this(id,actor,position,progress,SiloPayloadKind.NUCLEAR,VacuumPayloadProgress.EMPTY);
        }
        public Impact(UUID id,UUID actor,BlockPos position){this(id,actor,position,NuclearPayloadProgress.EMPTY);}
        public static Impact of(UUID id,UUID actor,BlockPos position,SiloPayloadKind kind) {
            return new Impact(id,actor,position,NuclearPayloadProgress.EMPTY,kind,VacuumPayloadProgress.EMPTY);
        }
        public Impact {
            if(id==null||actor==null||position==null||progress==null||kind==null||vacuum==null
                    ||kind==SiloPayloadKind.NUCLEAR&&!vacuum.equals(VacuumPayloadProgress.EMPTY)
                    ||kind==SiloPayloadKind.DOGEGG&&!progress.equals(NuclearPayloadProgress.EMPTY))throw new IllegalArgumentException("Invalid impact");
            position=position.immutable();
        }
    }
    private UUID facility;
    private BlockPos anchor;
    private boolean unfolding;
    private final Map<UUID, Flight> flights = new LinkedHashMap<>();
    private final Map<UUID, Impact> impacts = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> tickets = new LinkedHashMap<>();
    private final Set<UUID> receipts = new LinkedHashSet<>();
    private CompoundTag invalid;

    public static NuclearSiloStore get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Nuclear state requires server thread");
        return level.getDataStorage().computeIfAbsent(NuclearSiloStore::load, NuclearSiloStore::new, DATA_NAME);
    }
    public boolean writable() { return invalid == null; }
    public UUID facility() { return facility; }
    public BlockPos anchor() { return anchor; }
    public boolean canPlace() { return writable() && facility == null && flights.size()+impacts.size()<MAX_PENDING; }
    public boolean unfolding() { return unfolding; }
    public boolean beginUnfold(UUID id,BlockPos position) {
        if(!canPlace()||!claim(id,position))return false;
        unfolding=true;setDirty();return true;
    }
    public void finishUnfold(UUID id) {if(writable()&&unfolding&&id.equals(facility)){unfolding=false;setDirty();}}
    public boolean claim(UUID id, BlockPos position) {
        if (!writable() || id == null || position == null || (facility != null && (!facility.equals(id) || !position.equals(anchor)))) return false;
        if (facility == null && flights.size()+impacts.size()>=MAX_PENDING) return false;
        if (facility == null) { facility=id;anchor=position.immutable();setDirty(); }
        return true;
    }
    public void releaseFacility(UUID id) {
        if (writable() && id != null && id.equals(facility)) { facility=null;anchor=null;unfolding=false;setDirty(); }
    }
    public boolean reserve(Flight flight) {
        if (!writable() || flight == null || flight.committed() || !flight.silo().equals(facility) || flights.size()+impacts.size() >= MAX_FLIGHTS
                || receipts.contains(flight.id()) || flights.containsKey(flight.id()) || impacts.containsKey(flight.id())) return false;
        flights.put(flight.id(),flight);setDirty();return true;
    }
    public boolean committed(UUID id) { return receipts.contains(id); }
    /** One emergency payload for a destroyed, loaded facility; physical ammunition remains the caller's native inventory. */
    public boolean detonateFacility(UUID id, UUID actor, BlockPos position) {
        return detonateFacility(id,actor,position,SiloPayloadKind.NUCLEAR);
    }
    public boolean detonateFacility(UUID id, UUID actor, BlockPos position,SiloPayloadKind kind) {
        if (!writable() || id == null || actor == null || position == null || !id.equals(facility)
                || !position.equals(anchor) || unfolding || receipts.contains(id)
                || flights.size() + impacts.size() >= MAX_PENDING) return false;
        impacts.put(id, Impact.of(id, actor, position,kind));
        receipts.add(id);trimReceipts();setDirty();return true;
    }
    public Flight flight(UUID id) { return flights.get(id); }
    public List<Flight> flights() { return List.copyOf(flights.values()); }
    public List<Impact> pendingImpacts() { return List.copyOf(impacts.values()); }
    public Impact pendingImpact(UUID id) { return impacts.get(id); }
    public void advanceImpact(UUID id,NuclearPayloadProgress progress) {
        Impact old=impacts.get(id);
        if(!writable()||old==null||old.kind()!=SiloPayloadKind.NUCLEAR)throw new IllegalStateException("Nuclear impact missing");
        if((old.progress().started()&&(!progress.started()||progress.wave()!=old.progress().wave()))
                ||progress.terrainCursor()<old.progress().terrainCursor()
                ||progress.fluidCursor()<old.progress().fluidCursor()
                ||progress.fireCursor()<old.progress().fireCursor()
                ||progress.terrainRadius()!=old.progress().terrainRadius()
                ||progress.fluidRadius()!=old.progress().fluidRadius())throw new IllegalArgumentException("Payload rewind or resize");
        impacts.put(id,new Impact(id,old.actor(),old.position(),progress));setDirty();
    }
    public void advanceVacuum(UUID id,VacuumPayloadProgress progress) {
        Impact old=impacts.get(id);
        if(!writable()||old==null||old.kind()!=SiloPayloadKind.DOGEGG)throw new IllegalStateException("Vacuum impact missing");
        var before=old.vacuum();
        if(before.started()&&!progress.started()||progress.age()<before.age()
                ||progress.terrainCursor()<before.terrainCursor()||before.burst()&&!progress.burst()
                ||progress.lastTick()<before.lastTick()||progress.blastCursor()<before.blastCursor()
                ||progress.captured()!=before.captured()&&!new java.util.HashSet<>(progress.captured()).containsAll(before.captured())
                ||progress.captured().size()>before.captured().size()&&(before.burst()||progress.age()>=VacuumPayloadProgress.PULL_TICKS))
            throw new IllegalArgumentException("Vacuum payload rewind or late capture");
        impacts.put(id,new Impact(id,old.actor(),old.position(),old.progress(),old.kind(),progress));setDirty();
    }
    public void finishImpact(UUID id) {if(writable()&&impacts.remove(id)!=null)setDirty();}
    public void commit(UUID id) {
        Flight f=flights.get(id);
        if (!writable() || f == null || f.committed()) throw new IllegalStateException("Launch reservation missing");
        flights.put(id,new Flight(id,f.actor(),f.silo(),f.expires(),f.lastPosition(),true,f.kind()));
        receipts.add(id);
        trimReceipts();
        setDirty();
    }
    private void trimReceipts() {
        for(var iterator=receipts.iterator();receipts.size()>256&&iterator.hasNext();){
            UUID old=iterator.next();if(!flights.containsKey(old)&&!impacts.containsKey(old))iterator.remove();
        }
    }
    public void observe(UUID id, BlockPos position) {
        Flight f=flights.get(id);
        if (writable() && f != null && !f.lastPosition().equals(position)) {
            flights.put(id,new Flight(id,f.actor(),f.silo(),f.expires(),position.immutable(),f.committed(),f.kind()));setDirty();
        }
    }
    /** Transfer a landed flight to the persistent payload queue exactly once. */
    public boolean impact(UUID id, BlockPos position) {
        Flight f=flights.get(id);
        if (!writable() || f == null || !f.committed() || !tickets(id).isEmpty()) return false;
        impacts.put(id,Impact.of(id,f.actor(),position.immutable(),f.kind()));flights.remove(id);setDirty();return true;
    }
    public void forgetFlight(UUID id) { if (writable() && flights.remove(id)!=null) setDirty(); }
    public Set<Long> tickets(UUID id) { return tickets.getOrDefault(id,Set.of()); }
    public void tickets(UUID id, Set<Long> chunks) {
        if (!writable() || id==null || chunks.size()>(id.equals(facility)?1:3)
                ||(!chunks.isEmpty()&&!id.equals(facility)&&!flights.containsKey(id))) throw new IllegalArgumentException("Invalid rolling ticket set");
        if (chunks.equals(tickets(id))) return;
        if (chunks.isEmpty()) tickets.remove(id);else tickets.put(id,Set.copyOf(chunks));setDirty();
    }
    public boolean validTicketOwner(UUID id, long time) {
        if (!writable()) return false;
        if (id.equals(facility)) return tickets(id).size()<=1;
        Flight f=flights.get(id);return f!=null && f.expires()>time && tickets(id).size()<=3;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!writable()) return invalid.copy();
        tag.putInt("Schema",2);
        if(facility!=null){tag.putUUID("Facility",facility);tag.putLong("Anchor",anchor.asLong());}
        tag.putBoolean("Unfolding",unfolding);
        ListTag fs=new ListTag();
        for(Flight f:flights.values()) {
            CompoundTag v=new CompoundTag();v.putUUID("Id",f.id());v.putUUID("Actor",f.actor());v.putUUID("Silo",f.silo());
            v.putLong("Expires",f.expires());v.putLong("Position",f.lastPosition().asLong());v.putBoolean("Committed",f.committed());
            v.putString("Kind",f.kind().name());fs.add(v);
        }
        tag.put("Flights",fs);ListTag is=new ListTag();
        for(Impact i:impacts.values()) {
            CompoundTag v=new CompoundTag();v.putUUID("Id",i.id());v.putUUID("Actor",i.actor());v.putLong("Position",i.position().asLong());
            v.putString("Kind",i.kind().name());
            if(i.kind()==SiloPayloadKind.NUCLEAR)v.put("Payload",i.progress().save());else v.put("Vacuum",i.vacuum().save());
            is.add(v);
        }
        tag.put("Impacts",is);ListTag ts=new ListTag();
        tickets.forEach((id,chunks)->{
            CompoundTag v=new CompoundTag();v.putUUID("Id",id);v.putLongArray("Chunks",chunks.stream().mapToLong(Long::longValue).toArray());ts.add(v);
        });tag.put("Tickets",ts);
        ListTag rs=new ListTag();for(UUID id:receipts){CompoundTag v=new CompoundTag();v.putUUID("Id",id);rs.add(v);}tag.put("Receipts",rs);
        return tag;
    }
    public static NuclearSiloStore load(CompoundTag tag) {
        NuclearSiloStore s=new NuclearSiloStore();
        try {
            require(tag,"Schema",Tag.TAG_INT);int schema=tag.getInt("Schema");
            if(schema!=1&&schema!=2)throw new IllegalArgumentException("Unknown silo schema");
            if(tag.contains("Facility")){s.facility=uuid(tag,"Facility");require(tag,"Anchor",Tag.TAG_LONG);s.anchor=BlockPos.of(tag.getLong("Anchor"));}
            else if(tag.contains("Anchor"))throw new IllegalArgumentException("Orphan facility anchor");
            if(tag.contains("Unfolding")){require(tag,"Unfolding",Tag.TAG_BYTE);s.unfolding=tag.getBoolean("Unfolding");
                if(s.unfolding&&s.facility==null)throw new IllegalArgumentException("Orphan unfolding lease");}
            for(Tag t:list(tag,"Flights",MAX_FLIGHTS)) {
                CompoundTag v=(CompoundTag)t;require(v,"Expires",Tag.TAG_LONG);require(v,"Position",Tag.TAG_LONG);require(v,"Committed",Tag.TAG_BYTE);
                UUID id=uuid(v,"Id");Flight f=new Flight(id,uuid(v,"Actor"),uuid(v,"Silo"),v.getLong("Expires"),
                        BlockPos.of(v.getLong("Position")),v.getBoolean("Committed"),kind(v,schema));
                if(f.expires()<0||s.flights.put(id,f)!=null)throw new IllegalArgumentException("Duplicate flight");
            }
            for(Tag t:list(tag,"Impacts",MAX_PENDING)) {
                CompoundTag v=(CompoundTag)t;require(v,"Position",Tag.TAG_LONG);UUID id=uuid(v,"Id");
                var kind=kind(v,schema);NuclearPayloadProgress progress=NuclearPayloadProgress.EMPTY;
                var vacuum=VacuumPayloadProgress.EMPTY;
                if(kind==SiloPayloadKind.NUCLEAR&&v.contains("Payload")){
                    require(v,"Payload",Tag.TAG_COMPOUND);progress=NuclearPayloadProgress.load(v.getCompound("Payload"));
                }else if(kind==SiloPayloadKind.DOGEGG){require(v,"Vacuum",Tag.TAG_COMPOUND);vacuum=VacuumPayloadProgress.load(v.getCompound("Vacuum"));}
                if(kind==SiloPayloadKind.NUCLEAR&&v.contains("Vacuum")||kind==SiloPayloadKind.DOGEGG&&v.contains("Payload"))
                    throw new IllegalArgumentException("Mixed payload progress");
                if(s.flights.containsKey(id)||s.impacts.put(id,new Impact(id,uuid(v,"Actor"),BlockPos.of(v.getLong("Position")),progress,kind,vacuum))!=null)
                    throw new IllegalArgumentException("Duplicate impact");
            }
            if(s.flights.size()+s.impacts.size()>MAX_PENDING)throw new IllegalArgumentException("Payload budget exceeded");
            for(Tag t:list(tag,"Tickets",MAX_FLIGHTS+1)) {
                CompoundTag v=(CompoundTag)t;require(v,"Chunks",Tag.TAG_LONG_ARRAY);UUID id=uuid(v,"Id");long[] cs=v.getLongArray("Chunks");
                if(cs.length>(id.equals(s.facility)?1:3)||cs.length==0
                        ||(!id.equals(s.facility)&&!s.flights.containsKey(id)&&!s.impacts.containsKey(id)))
                    throw new IllegalArgumentException("Invalid ticket owner");
                Set<Long> set=new LinkedHashSet<>();for(long c:cs)set.add(c);
                if(set.size()!=cs.length||s.tickets.put(id,Set.copyOf(set))!=null)throw new IllegalArgumentException("Duplicate tickets");
            }
            for(Tag t:list(tag,"Receipts",256))if(!s.receipts.add(uuid((CompoundTag)t,"Id")))throw new IllegalArgumentException("Duplicate receipt");
            for(Flight f:s.flights.values())if(f.committed()!=s.receipts.contains(f.id()))throw new IllegalArgumentException("Inconsistent launch receipt");
            for(Impact i:s.impacts.values())if(!s.receipts.contains(i.id()))throw new IllegalArgumentException("Impact receipt missing");
            // 1.1.50 could persist a committed impact before releasing its flight tickets.
            // Recover only after every record/receipt validates. Native ticket loading rejects
            // these retired owners; payload progress and the facility's cycle remain intact.
            if(s.tickets.keySet().removeIf(id->s.impacts.containsKey(id)&&!id.equals(s.facility)))s.setDirty();
        }catch(RuntimeException malformed){s.invalid=tag.copy();s.facility=null;s.anchor=null;s.flights.clear();s.impacts.clear();s.tickets.clear();s.receipts.clear();}
        return s;
    }
    private static UUID uuid(CompoundTag t,String k){if(!t.hasUUID(k))throw new IllegalArgumentException(k);return t.getUUID(k);}
    private static SiloPayloadKind kind(CompoundTag tag,int schema){
        if(schema==1){if(tag.contains("Kind"))throw new IllegalArgumentException("Payload in old schema");return SiloPayloadKind.NUCLEAR;}
        require(tag,"Kind",Tag.TAG_STRING);return SiloPayloadKind.read(tag.getString("Kind"));
    }
    private static void require(CompoundTag t,String k,int type){if(!t.contains(k,type))throw new IllegalArgumentException(k);}
    private static ListTag list(CompoundTag t,String k,int max){
        require(t,k,Tag.TAG_LIST);ListTag l=t.getList(k,Tag.TAG_COMPOUND);
        if(l.size()>max||((ListTag)t.get(k)).size()!=l.size())throw new IllegalArgumentException(k);return l;
    }
}
