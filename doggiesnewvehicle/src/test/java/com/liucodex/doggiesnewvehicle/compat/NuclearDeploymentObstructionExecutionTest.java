package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/** Execute the exact final obstruction predicate and durable player diagnostic, with query/message edges. */
class NuclearDeploymentObstructionExecutionTest {
    @Test void snowDropsDeadAndSpectatorEntitiesCannotBlockButLiveBodiesIdentifyThemselves() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerPlayer",Operator.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/item/ItemEntity",Drop.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/network/chat/Component",Text.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/network/chat/MutableComponent",Message.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/artillery/silo/NuclearContainerDeployment",
                Set.of("rejectObstruction","obstructs"),names,null);
        var method=type.getMethod("rejectObstruction",World.class,BlockPos.class,Operator.class);
        var world=new World();var player=new Operator();var centre=new BlockPos(-6580,69,568);
        var drop=new Drop();drop.position=centre;
        var dead=new Body();dead.alive=false;dead.position=centre;
        var spectator=new Body();spectator.spectator=true;spectator.position=centre;
        world.entities.addAll(List.of(drop,dead,spectator));
        assertEquals(false,method.invoke(null,world,centre,player));assertTrue(player.messages.isEmpty());
        var obstruction=new Body();obstruction.position=centre.above();world.entities.add(obstruction);
        assertEquals(true,method.invoke(null,world,centre,player));assertEquals(1,player.messages.size());
        var message=player.messages.get(0);assertEquals("message.doggiesnewvehicle.nuclear.unfold_entity_detail",message.key);
        assertSame(obstruction.name,message.arguments[0]);assertEquals(-6580,message.arguments[1]);assertEquals(70,message.arguments[2]);assertEquals(568,message.arguments[3]);
        assertFalse(player.actionBar,"The actual offender must remain readable in chat");
        assertEquals(centre.getY()-10,world.query.minY);assertEquals(9,world.query.getXsize());assertEquals(9,world.query.getZsize());
        world.entities.clear();player.position=centre;world.entities.add(player);
        assertEquals(true,method.invoke(null,world,centre,player),"The operator also needs to leave the shaft before the final spawn");
    }
    public interface Text {static Message translatable(String key,Object... arguments){return new Message(key,arguments);}}
    public static class Message implements Text {public final String key;public final Object[] arguments;
        public Message(String key,Object[] arguments){this.key=key;this.arguments=arguments;}}
    public static class Body {public boolean alive=true,spectator;public BlockPos position=BlockPos.ZERO;
        public final Text name=new Message("entity.test",new Object[0]);public boolean isSpectator(){return spectator;}
        public boolean isAlive(){return alive;}public BlockPos blockPosition(){return position;}public Text getDisplayName(){return name;}}
    public static class Drop extends Body { }
    public static class Operator extends Body {public final List<Message> messages=new ArrayList<>();public boolean actionBar;
        public void displayClientMessage(Text message,boolean actionBar){messages.add((Message)message);this.actionBar=actionBar;}}
    public static class World {public final List<Body> entities=new ArrayList<>();public AABB query;
        public List<Body> getEntities(Body excluded,AABB area,Predicate<Body> predicate){query=area;
            return entities.stream().filter(e->new AABB(e.position).intersects(area)).filter(predicate).toList();}}
}
