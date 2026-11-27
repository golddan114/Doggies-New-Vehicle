package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/** Execute the compiled facility callback and pinned SBW consumeEnergy. World, inventory and FE storage edges are doubles. */
public class NuclearNativeBoundaryTest {
    static final String ROOT="com/liucodex/doggiesnewvehicle/artillery/silo/";
    static final String PROBE="com/liucodex/doggiesnewvehicle/compat/nuclearprobe/";
    @Test void compiledFacilityRejectionIdentifiesAmmoReloadEnergyAndActiveLaunchWithoutDebiting() throws Exception {
        var f=new Fixture();var reason=f.body.getClass().getMethod("indicatorRejectionKey",boolean.class);
        f.body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.EMPTY,0,null,null));
        f.body.ammo=0;assertEquals("message.doggiesnewvehicle.nuclear.indicator_ammo",reason.invoke(f.body,true));
        f.body.ammo=1;f.body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.RELOADING,6000,null,null));
        assertEquals("message.doggiesnewvehicle.nuclear.indicator_reload",reason.invoke(f.body,true));
        f.body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.READY,0,null,null));f.body.energy=19_999_999;
        assertEquals("message.doggiesnewvehicle.nuclear.indicator_energy",reason.invoke(f.body,true));
        assertEquals("message.doggiesnewvehicle.nuclear.indicator_target",reason.invoke(f.body,false));
        for(var phase: new SiloCycle.Phase[]{SiloCycle.Phase.OPENING,SiloCycle.Phase.DEPARTING,SiloCycle.Phase.CLOSING}){
            f.body.cycle.restore(new SiloCycle.Snapshot(phase,5,f.operation,new SiloCycle.Target(100,64,200)));
            f.body.ammo=0;f.body.energy=0;assertEquals("message.doggiesnewvehicle.nuclear.indicator_busy",reason.invoke(f.body,true));
        }
        assertEquals(0,f.body.energyWrites);assertNull(f.body.lastDebit);assertFalse(f.store.committed(f.operation));
    }
    @Test void compiledLaunchCallbackDebitsNativeFeAndCommitsBeforeArming() throws Exception {
        var f=new Fixture();assertTrue(SiloLaunchTransaction.execute(f.port));
        assertEquals(0,f.body.ammo);assertEquals(0,f.body.energy);assertEquals(1,f.body.energyWrites);assertEquals(f.operation,f.body.lastDebit);
        assertTrue(f.store.committed(f.operation));assertTrue(f.missile.armed);assertFalse(f.missile.discarded);
    }
    @Test void dogEggLaunchKeepsItsKindThroughNativeDebitAndMismatchedRackCannotSpend() throws Exception {
        var f=new Fixture(SiloPayloadKind.DOGEGG);assertTrue(SiloLaunchTransaction.execute(f.port));
        assertEquals(SiloPayloadKind.DOGEGG,f.store.flight(f.operation).kind());
        assertEquals(0,f.body.ammo);assertEquals(0,f.body.energy);assertTrue(f.missile.armed);
        f=new Fixture(SiloPayloadKind.DOGEGG);f.missile.kind=SiloPayloadKind.NUCLEAR;
        assertFalse(SiloLaunchTransaction.execute(f.port));assertEquals(1,f.body.ammo);assertEquals(20_000_000,f.body.energy);
        assertEquals(0,f.body.energyWrites);assertFalse(f.missile.armed);assertFalse(f.store.committed(f.operation));
    }
    @Test void existingRackLossAndInventoryMutationCannotSpendResources() throws Exception {
        var f=new Fixture();f.world.accepted=false;assertFalse(SiloLaunchTransaction.execute(f.port));
        assertEquals(20_000_000,f.body.energy);assertEquals(1,f.body.ammo);assertNull(f.body.lastDebit);assertNull(f.store.flight(f.operation));assertTrue(f.missile.discarded);
        f=new Fixture();Body owner=f.body;f.world.onSpawn=()->owner.ammo=0;assertFalse(SiloLaunchTransaction.execute(f.port));
        assertEquals(20_000_000,f.body.energy);assertEquals(0,f.body.energyWrites);assertFalse(f.store.committed(f.operation));assertNull(f.store.flight(f.operation));
        f=new Fixture();Missile candidate=f.missile;f.world.onSpawn=candidate::discard;
        assertFalse(SiloLaunchTransaction.execute(f.port));assertEquals(20_000_000,f.body.energy);assertEquals(1,f.body.ammo);assertNull(f.store.flight(f.operation));
        f=new Fixture();f.world.onSpawn=()->{throw new IllegalStateException("join event failure");};Fixture failed=f;
        assertThrows(IllegalStateException.class,()->SiloLaunchTransaction.execute(failed.port));assertNull(f.store.flight(f.operation));assertEquals(20_000_000,f.body.energy);
    }
    static class Fixture {
        final Body body;final World world=new World();final Missile missile=new Missile();final NuclearSiloStore store=new NuclearSiloStore();
        final UUID operation=UUID.randomUUID();final SiloLaunchTransaction.Port port;
        Fixture() throws Exception {this(SiloPayloadKind.NUCLEAR);}
        Fixture(SiloPayloadKind kind) throws Exception {
            var names=new HashMap<String,String>();
            names.put(ROOT+"NuclearSiloEntity",PROBE+"DebitBody");names.put(ROOT+"NuclearSiloEntity$1",PROBE+"Port");
            names.put(ROOT+"NuclearMissileEntity",Type.getInternalName(Missile.class));names.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(World.class));
            names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Missile.class));
            names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
            names.put("com/atsuishio/superbwarfare/capability/energy/SyncedEntityEnergyStorage",Type.getInternalName(Storage.class));
            names.put("net/neoforged/neoforge/energy/IEnergyStorage",Type.getInternalName(StoragePort.class));
            String nativeOwner="com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity";names.put(nativeOwner,PROBE+"DebitBody");
            ClassNode debit;
            try(var zip=new ZipFile(SbwTestDependency.path().toFile())){debit=node(zip.getInputStream(zip.getEntry(nativeOwner+".class")).readAllBytes());}
            debit.access=Opcodes.ACC_PUBLIC;debit.superName=Type.getInternalName(Body.class);debit.interfaces.clear();debit.fields.clear();debit.innerClasses.clear();debit.nestHostClass=null;debit.nestMembers=null;
            debit.methods.removeIf(m->!m.name.equals("consumeEnergy"));
            try(var in=getClass().getResourceAsStream("/"+ROOT+"NuclearSiloEntity.class")){
                node(in.readAllBytes()).methods.stream().filter(m->m.name.equals("indicatorRejectionKey")).forEach(debit.methods::add);
            }
            debit.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC);
            var ctor=new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESPECIAL,debit.superName,"<init>","()V",false));ctor.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));debit.methods.add(ctor);
            ClassNode callback;
            try(var in=getClass().getResourceAsStream("/"+ROOT+"NuclearSiloEntity$1.class")){callback=node(in.readAllBytes());}
            callback.access=Opcodes.ACC_PUBLIC;callback.innerClasses.clear();callback.nestHostClass=null;callback.nestMembers=null;callback.outerClass=null;
            callback.methods.forEach(m->m.access=Opcodes.ACC_PUBLIC);
            var definitions=Map.of(PROBE+"DebitBody",remap(debit,names),PROBE+"Port",remap(callback,names));
            var loader=new ClassLoader(getClass().getClassLoader()){
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException{byte[] bytes=definitions.get(name.replace('.','/'));if(bytes==null)return super.findClass(name);return defineClass(name,bytes,0,bytes.length);}
            };
            body=(Body)loader.loadClass((PROBE+"DebitBody").replace('/','.')).getConstructor().newInstance();
            body.kind=kind;missile.kind=kind;
            body.cycle.restore(new SiloCycle.Snapshot(SiloCycle.Phase.OPENING,0,operation,new SiloCycle.Target(100,64,200)));
            store.claim(body.id,BlockPos.ZERO);store.reserve(new NuclearSiloStore.Flight(operation,UUID.randomUUID(),body.id,48000,BlockPos.ZERO,false,kind));missile.store=store;missile.id=operation;
            missile.silo=body.id;world.missile=missile;
            var constructor=loader.loadClass((PROBE+"Port").replace('/','.')).getConstructors()[0];Object[] values=new Object[constructor.getParameterCount()];
            Class<?>[] types=constructor.getParameterTypes();
            for(int i=0;i<types.length;i++)values[i]=types[i].isInstance(body)?body:types[i]==World.class?world:types[i]==Missile.class?missile:types[i]==BlockPos.class?new BlockPos(100,64,200):types[i]==NuclearSiloStore.class?store:types[i]==UUID.class?operation:null;
            port=(SiloLaunchTransaction.Port)constructor.newInstance(values);
        }
    }
    static ClassNode node(byte[] bytes){var n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.SKIP_FRAMES);return n;}
    static byte[] remap(ClassNode node,Map<String,String> names){
        var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer,new Remapper(){
            @Override public String map(String name){return names.getOrDefault(name,name);}
            @Override public String mapMethodName(String owner,String name,String descriptor){return name.equals("m_9236_")?"level":name;}
            @Override public String mapFieldName(String owner,String name,String descriptor){return name.equals("f_46443_")?"isClientSide":name;}
        }));return writer.toByteArray();
    }
    public static class Body {
        public SiloPayloadKind kind=SiloPayloadKind.NUCLEAR;
        public SiloPayloadKind payloadKind(){return kind;}
        public int ammo=1,energy=20_000_000,energyWrites;public UUID id=UUID.randomUUID(),lastDebit;public CompoundTag invalidData;
        public final SiloCycle cycle=new SiloCycle(NuclearRules.DEFAULT);
        public final World world=new World();public World level(){return world;}
        public final StoragePort energyStorage=new Storage(this);
        public boolean isRemoved(){return false;}public float getHealth(){return 100;}public boolean validTarget(BlockPos position){return true;}
        public int getEnergy(){return energy;}public void setEnergy(int value){energy=value;energyWrites++;}public int ammoSlot(){return ammo>0?0:-1;}public UUID getUUID(){return id;}
        public boolean hasEnergyStorage(){return true;}
        public Storage getEnergyStorage(){return new Storage(this);}
        public net.minecraft.world.item.ItemStack removeItem(int slot,int count){assertEquals(0,slot);assertEquals(1,count);ammo-=count;return null;}
    }
    public static class World {public boolean isClientSide,accepted=true;public Missile missile;public Runnable onSpawn=()->{};
        public Missile getEntity(UUID id){onSpawn.run();return accepted&&missile.id.equals(id)?missile:null;}}
    public interface StoragePort {int extractEnergy(int amount,boolean simulate);}
    public static class Storage implements StoragePort {
        final Body owner;public Storage(Body owner){this.owner=owner;}
        public int extractEnergy(int amount,boolean simulate){assertEquals(20_000_000,amount);assertFalse(simulate);owner.energyWrites++;int actual=Math.min(amount,owner.energy);owner.energy-=actual;return actual;}
    }
    public static class Missile {public boolean armed,discarded;public NuclearSiloStore store;public UUID id,silo;
        public SiloPayloadKind kind=SiloPayloadKind.NUCLEAR;
        public SiloPayloadKind payloadKind(){return kind;}
        public boolean belongsToRack(UUID owner){return !armed&&!discarded&&owner.equals(silo);}
        public boolean isRemoved(){return discarded;}public void discard(){discarded=true;}public void arm(){assertTrue(store.committed(id));armed=true;}}
}
