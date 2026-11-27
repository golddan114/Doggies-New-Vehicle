package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Runs the production crew pruning branch on both platform projections, including actual attachment cleanup. */
class DnvCrewRemovalExecutionTest {
    Class<?> target() throws Exception {
        var names=new HashMap<String,String>();
        for(String name:List.of("net/minecraft/server/level/ServerLevel","net/minecraft/world/level/Level"))
            NuclearRecoveryExecutionTest.map(names,name,World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/MinecraftServer",Server.class);
        for(String name:List.of("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity","net/minecraft/world/entity/Entity"))
            NuclearRecoveryExecutionTest.map(names,name,Body.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewAttachment",Attachment.class);
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewCapability",Caps.class);
        for(String prefix:List.of("net/minecraftforge/","net/neoforged/neoforge/")){
            NuclearRecoveryExecutionTest.map(names,prefix+"common/capabilities/Capability",Object.class);
            NuclearRecoveryExecutionTest.map(names,prefix+"common/util/LazyOptional",Optional.class);
            NuclearRecoveryExecutionTest.map(names,prefix+"common/util/NonNullConsumer",java.util.function.Consumer.class);
        }
        return NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/DnvVehicleCrewServices",
                Set.of("synchronizeTracked","tickTrackedAutonomy","untrack","registered"),names,Services.class,false);
    }
    @Test void lostRegistryAndDirectRemovedVehiclesClearTheirCrewBeforeAnotherAutonomyTick() throws Exception {
        var type=target();var server=new Server();var world=new World(server);
        var live=new Body(world);var missing=new Body(world);var removed=new Body(world);removed.removed=true;
        Services.TRACKED.clear();var vehicles=new LinkedHashMap<UUID,Body>();
        for(var body:List.of(live,missing,removed))vehicles.put(body.id,body);
        Services.TRACKED.put(server,vehicles);world.entities.put(live.id,live);
        type.getMethod("tickTrackedAutonomy",Server.class).invoke(null,server);
        assertEquals(Set.of(live.id),vehicles.keySet());assertEquals(1,live.attachment.ticks);
        assertEquals(1,missing.attachment.clears);assertEquals(1,removed.attachment.clears);
        assertEquals(0,missing.attachment.ticks);assertEquals(0,removed.attachment.ticks);
        type.getMethod("synchronizeTracked",Server.class).invoke(null,server);assertEquals(1,live.synchronizations);
        assertEquals(1,missing.attachment.clears);assertEquals(1,removed.attachment.clears);
        world.entities.clear();type.getMethod("synchronizeTracked",Server.class).invoke(null,server);
        assertFalse(Services.TRACKED.containsKey(server));assertEquals(1,live.attachment.clears);
    }
    public static class Services {
        public static final Map<Server,Map<UUID,Body>> TRACKED=new IdentityHashMap<>();
        public static void synchronizeVehicle(Body body){body.synchronizations++;}
    }
    public static class Server { }
    public static class World {
        public final Server server;public final Map<UUID,Body> entities=new HashMap<>();public World(Server server){this.server=server;}
        public Server getServer(){return server;}public Body getEntity(UUID id){return entities.get(id);}
    }
    public static class Body {
        public final UUID id=UUID.randomUUID();public final World world;public final Attachment attachment=new Attachment();
        public boolean removed;public int synchronizations;public Body(World world){this.world=world;}
        public boolean isRemoved(){return removed;}public World level(){return world;}public UUID getUUID(){return id;}
        public Optional<Attachment> getCapability(Object token){return Optional.of(attachment);}
    }
    public static class Caps {
        public static final Object CAPABILITY=new Object();public static Optional<Attachment> find(Body body){return Optional.of(body.attachment);}
    }
    public static class Attachment {public int ticks,clears;public void tickAutonomy(){ticks++;}public void clearOnUnload(){clears++;}}
}
