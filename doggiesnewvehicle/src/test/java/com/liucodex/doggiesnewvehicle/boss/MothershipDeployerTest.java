package com.liucodex.doggiesnewvehicle.boss;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the real compiled useOn transaction with bounded world/registration ports; no game startup. */
public class MothershipDeployerTest {
    private static final String SOURCE="com/liucodex/doggiesnewvehicle/item/DnvMothershipDeployerItem";
    private static Class<?> executable;

    @Test void survivalPlacesOneShipAtGroundPlusTwentyThenAssignsTeamAndConsumes() throws Exception {
        Context c=new Context();c.player.yaw=75;
        assertEquals(InteractionResult.CONSUME,use(c));
        assertEquals(new Vec3(10.5,99,20.5),((Server)c.level).spawned.position());
        assertEquals(75,((Server)c.level).spawned.yaw);assertEquals(1,c.stack.count);
        assertTrue(((Server)c.level).spawned.teamAssigned);
        assertTrue(((Server)c.level).spawned.boundsReady);
        assertEquals(new Vec3(10.5,64,20.5),((Server)c.level).spawned.beaconAnchor);
    }
    @Test void creativeDoesNotConsumeAndClientNeverCreatesOrConsumes() throws Exception {
        Context c=new Context();c.player.abilities.instabuild=true;
        assertEquals(InteractionResult.CONSUME,use(c));assertEquals(2,c.stack.count);
        Context client=new Context();client.level=new Level();
        assertEquals(InteractionResult.SUCCESS,use(client));assertEquals(2,client.stack.count);
        assertEquals(0,client.level.created);
    }
    @Test void permissionFailuresAreRejectedBeforeEntityCreation() throws Exception {
        for(int kind=0;kind<6;kind++) {
            Context c=new Context();
            switch(kind) {
                case 0 -> c.player=null;
                case 1 -> c.player.abilities.mayBuild=false;
                case 2 -> c.player.spectator=true;
                case 3 -> c.player.mayUse=false;
                case 4 -> ((Server)c.level).permission=false;
                case 5 -> c.stack.count=0;
            }
            int count=c.stack.count;
            assertEquals(InteractionResult.FAIL,use(c));assertEquals(0,c.level.created);
            assertEquals(0,((Server)c.level).insertions);assertEquals(count,c.stack.count);
        }
    }
    @Test void blockedAirspaceAndRejectedInsertionPreserveTheSummoner() throws Exception {
        Context blocked=new Context();((Server)blocked.level).space=false;
        assertEquals(InteractionResult.FAIL,use(blocked));assertEquals(2,blocked.stack.count);
        assertEquals(0,((Server)blocked.level).insertions);assertTrue(blocked.player.message.endsWith(".space"));
        Context refused=new Context();((Server)refused.level).accept=false;
        assertEquals(InteractionResult.FAIL,use(refused));assertEquals(2,refused.stack.count);
        assertEquals(1,((Server)refused.level).insertions);assertNull(((Server)refused.level).spawned);
        assertTrue(refused.player.message.endsWith(".failed"));
        Context absent=new Context();absent.level.create=false;
        assertEquals(InteractionResult.FAIL,use(absent));assertEquals(2,absent.stack.count);
        assertEquals(0,((Server)absent.level).insertions);
    }
    @Test void occupiedDimensionRejectsASecondShipBeforePlacement() throws Exception {
        Context occupied = new Context();
        ((Server) occupied.level).presence.owner = UUID.randomUUID();
        assertEquals(InteractionResult.FAIL, use(occupied));
        assertEquals(0, ((Server) occupied.level).insertions);
        assertEquals(2, occupied.stack.count);
        assertTrue(occupied.player.message.endsWith(".limit"));
    }
    @Test void itemRegistrationCreativeEntryAndBothTranslationsArePresent() throws Exception {
        String registry=Files.readString(Path.of("src/boss/java/com/liucodex/doggiesnewvehicle/boss/DnvBossContent.java"));
        assertTrue(registry.contains("ITEMS.register(\"ses_executor_of_truth_deployer\""));
        assertTrue(registry.contains("ITEMS.register(modBus)"));
        assertTrue(registry.contains("output.accept(MOTHERSHIP_DEPLOYER.get())"));
        assertTrue(registry.contains("ENTITY_TYPES.register(\"ses_executor_of_truth\""));
        assertTrue(registry.contains("build(\"doggievehicle:ses_executor_of_truth\")"));
        assertTrue(Files.exists(Path.of("src/boss/resources/data/doggievehicle/sbw/vehicles/ses_executor_of_truth.json")));
        for(String lang:new String[]{"zh_cn","en_us"}) {
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/boss/lang/"+lang+".json"))).getAsJsonObject();
            assertFalse(json.get("item.doggiesnewvehicle.ses_executor_of_truth_deployer").getAsString().isBlank());
            for(String suffix:new String[]{"denied","space","failed","limit"})
                assertTrue(json.has("message.doggiesnewvehicle.ses_executor_of_truth_deployer."+suffix));
            var entityLang=com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                    "src/boss/resources/assets/dnv_mothership/lang/"+lang+".json"))).getAsJsonObject();
            assertEquals(lang.equals("zh_cn")?"SES 真理执行者":"SES Executor of Truth",
                    entityLang.get("entity.doggievehicle.ses_executor_of_truth").getAsString());
        }
        var model=com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                "src/boss/resources/assets/doggiesnewvehicle/models/item/ses_executor_of_truth_deployer.json"))).getAsJsonObject();
        assertEquals("minecraft:item/ender_eye",model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    private static InteractionResult use(Context context) throws Exception {
        if(executable==null) {
            ClassNode node=new ClassNode();
            try(var input=MothershipDeployerTest.class.getClassLoader().getResourceAsStream(SOURCE+".class")) {
                new ClassReader(java.util.Objects.requireNonNull(input)).accept(node,0);
            }
            node.methods.removeIf(m->!m.name.equals("useOn")&&!m.name.equals("reject"));node.fields.clear();node.interfaces.clear();
            node.innerClasses.clear();node.superName=Type.getInternalName(Probe.class);
            MethodNode ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,node.superName,"<init>","()V",false);
            ctor.visitInsn(Opcodes.RETURN);ctor.maxStack=1;ctor.maxLocals=1;node.methods.add(ctor);
            Map<String,String> mapping=new HashMap<>();String target=SOURCE+"ExecutionProbe";mapping.put(SOURCE,target);
            for(var entry:Map.ofEntries(
                    Map.entry("net/minecraft/world/level/Level",Level.class),
                    Map.entry("net/minecraft/server/level/ServerLevel",Server.class),
                    Map.entry("net/minecraft/world/item/context/UseOnContext",Context.class),
                    Map.entry("net/minecraft/world/entity/player/Player",Player.class),
                    Map.entry("net/minecraft/world/entity/player/Abilities",Abilities.class),
                    Map.entry("net/minecraft/world/item/ItemStack",Stack.class),
                    Map.entry("net/minecraft/network/chat/Component",Message.class),
                    Map.entry("net/minecraft/world/entity/Entity",Actor.class),
                    Map.entry("net/minecraft/world/entity/EntityType",Factory.class),
                    Map.entry("net/minecraftforge/registries/RegistryObject",Holder.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/entity/DnvMothershipEntity",Ship.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/boss/DnvBossContent",Content.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/boss/MothershipWorld",Queries.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/boss/MothershipPresence",Presence.class),
                    Map.entry("com/liucodex/doggiesnewvehicle/compat/DnvBossTeamMembership",Teams.class)).entrySet())
                mapping.put(entry.getKey(),Type.getInternalName(entry.getValue()));
            // translatable's concrete return is MutableComponent.
            mapping.put("net/minecraft/network/chat/MutableComponent",Type.getInternalName(Text.class));
            ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(mapping)));
            byte[] bytes=writer.toByteArray();
            executable=new ClassLoader(MothershipDeployerTest.class.getClassLoader()) {
                Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
            }.define();
        }
        return (InteractionResult)executable.getMethod("useOn",Context.class).invoke(executable.getConstructor().newInstance(),context);
    }
    public static class Probe { }
    public static class Level {public int created;public boolean create=true;}
    public static class Server extends Level {
        public boolean permission=true,space=true,accept=true;public int insertions;public Ship spawned;
        public Presence presence = new Presence();
        public boolean mayInteract(Player player,BlockPos pos){return permission;}
        public boolean addFreshEntity(Actor ship){insertions++;if(!accept)return false;spawned=(Ship)ship;return true;}
    }
    public static class Actor { }
    public static class Ship extends Actor {
        public UUID id = UUID.randomUUID();
        public Level level;public Vec3 position,beaconAnchor;public float yaw;public boolean boundsReady,teamAssigned;
        public void moveTo(double x,double y,double z,float yaw,float pitch){position=new Vec3(x,y,z);this.yaw=yaw;}
        public Vec3 position(){return position;}public float getYRot(){return yaw;}
        public UUID getUUID(){return id;}
        public void setBeaconAnchor(Vec3 point){beaconAnchor=point;}
        public void updateOBB(){boundsReady=true;}public void refreshBoundingBoxFromOBBs(){assertTrue(boundsReady);}
    }
    public static class Factory {public Actor create(Level level){level.created++;if(!level.create)return null;Ship s=new Ship();s.level=level;return s;}}
    public static class Holder {public Object get(){return new Factory();}}
    public static class Content {public static final Holder MOTHERSHIP=new Holder();}
    public static class Teams {public static boolean assignIfUnassigned(Actor ship){((Ship)ship).teamAssigned=true;return true;}}
    public static class Queries {public static boolean safePose(Actor ship,Vec3 bottom,double yaw){return ((Server)((Ship)ship).level).space;}}
    public static class Presence {
        public UUID owner;
        public static Presence get(Server level) { return level.presence; }
        public boolean permitsFresh(Server level, UUID candidate) { return permitsFresh(candidate); }
        public boolean permitsFresh(UUID candidate) { return owner == null || owner.equals(candidate); }
    }
    public static class Abilities {public boolean mayBuild=true,instabuild;}
    public static class Stack {public int count=2;public boolean isEmpty(){return count==0;}public void shrink(int n){count-=n;}}
    public interface Message {String key();static Text translatable(String key){return new Text(key);}}
    public record Text(String key) implements Message { }
    public static class Player {
        public final Abilities abilities=new Abilities();public boolean spectator,mayUse=true;public float yaw;public String message="";
        public Abilities getAbilities(){return abilities;}public boolean isSpectator(){return spectator;}public float getYRot(){return yaw;}
        public boolean mayUseItemAt(BlockPos p,Direction d,Stack s){return mayUse;}
        public void displayClientMessage(Message m,boolean overlay){message=m.key();}
    }
    public static class Context {
        public Level level=new Server();public Player player=new Player();public final Stack stack=new Stack();
        public Level getLevel(){return level;}public Player getPlayer(){return player;}public Stack getItemInHand(){return stack;}
        public BlockPos getClickedPos(){return new BlockPos(10,63,20);}public Direction getClickedFace(){return Direction.UP;}
    }
}
