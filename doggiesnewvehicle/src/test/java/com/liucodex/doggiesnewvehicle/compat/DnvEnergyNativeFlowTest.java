package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liucodex.doggiesnewvehicle.artillery.energy.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Compiled hooks executed in native extract -> setEnergy -> receive order. */
class DnvEnergyNativeFlowTest {
    @Test void allPlayerContentsRefillOnInsertionAndSustainRealCostsWithoutTwentyTickFlicker() throws Exception {
        var capacities=Map.of("storm_turret",2000000,"50_changfeng_cannon",2000000,"m1_yunlong",200000,
                "jingzhe_turret",5000000,"blizzard",2000000,"apocalypse",5000000,"nuclear_silo",20000000);
        for(var vehicle:capacities.entrySet()){
            Object inventory=fixture("VehicleEnergyBudgetMixin",Set.of("dnv$chargeInventory","dnv$inventoryInterval"),InventoryBody.class);
            var body=(InventoryBody)inventory;body.capacity=vehicle.getValue();
            body.budget=DnvEnergyInputPolicy.forVehicle("doggievehicle:"+vehicle.getKey());
            var input=input(body);var battery=new Battery();battery.energy=100_000_000;
            var interval=inventory.getClass().getMethod("dnv$inventoryInterval",int.class);
            var extract=inventory.getClass().getMethod("dnv$chargeInventory",BatteryPort.class,int.class,boolean.class);
            assertEquals(1,interval.invoke(inventory,20));
            long spent=0;
            for(int tick=1;tick<=80;tick++){
                body.world.tick=tick;
                int actual=(int)extract.invoke(inventory,battery,body.capacity-body.energy,false);
                assertEquals(actual,limit(input,actual));credit(input,actual,false);body.energy+=actual;
                assertEquals(body.capacity,body.energy,"Zero-energy insertion or continued supply lagged: "+vehicle.getKey());
                int cost=vehicle.getKey().equals("nuclear_silo")&&tick==40?20_000_000:
                        vehicle.getKey().equals("blizzard")?3144:vehicle.getKey().equals("50_changfeng_cannon")?60_000:16;
                body.energy-=cost;spent+=cost;
                assertEquals(100_000_000L,battery.energy+(long)body.energy+spent);
            }
            body.world.isClientSide=true;assertEquals(20,interval.invoke(inventory,20));
            body.world.isClientSide=false;body.budget=null;assertEquals(20,interval.invoke(inventory,20));
        }
    }
    @Test void inventoryAndNativeCreditSpendOneBudgetAndConserveEnergy() throws Exception {
        Object inventory = fixture("VehicleEnergyBudgetMixin", Set.of("dnv$chargeInventory"), InventoryBody.class);
        var body = (InventoryBody) inventory;
        var input = input(body);
        var battery = new Battery(); battery.energy = 20_000_000;
        var extract = inventory.getClass().getMethod("dnv$chargeInventory", BatteryPort.class, int.class, boolean.class);
        int actual = (int) extract.invoke(inventory, battery, 2_000_000, false);
        assertEquals(2048, actual); assertEquals(2048, body.budget.allowance(1, 99999));
        assertEquals(actual, limit(input, actual));
        credit(input, actual, false); body.energy += actual;
        assertEquals(20_000_000, battery.energy + body.energy);
        assertEquals(0, limit(input, 100));
        assertEquals(0, extract.invoke(inventory, battery, 9999, false));
        body.world.tick++; actual = (int) extract.invoke(inventory, battery, 10, true);
        credit(input, actual, true);
        assertEquals(2048, body.budget.allowance(2, 99999));
        assertEquals(20_000_000, battery.energy + body.energy);
    }
    @Test void autonomousExportStopsButScopedNativeSupplyAndConsumptionRemainAvailable() throws Exception {
        var body = new AutonomousBody(); body.budget = new DnvEnergyInputBudget(0);
        var hook = input(body);
        assertEquals(0, limit(hook, 5_000_000));
        var callback = new CallbackInfoReturnable<Integer>("extract", true, 123);
        var extract = hook.getClass().getMethod("dnv$excludeAutonomousExport", int.class, boolean.class, CallbackInfoReturnable.class);
        extract.invoke(hook, 100, false, callback);
        assertTrue(callback.isCancelled()); assertEquals(0, callback.getReturnValueI());
        body.mutation = true;
        assertEquals(5_000_000, limit(hook, 5_000_000));
        callback = new CallbackInfoReturnable<>("extract", true, 123);
        extract.invoke(hook, 100, false, callback); assertFalse(callback.isCancelled());
        credit(hook, 5_000_000, false);
        body.mutation = false; assertEquals(0, limit(hook, 5_000_000));
    }
    @Test void oldOverCapacityEnergyIsPreservedAndCannotReturnNegativeReceiveOrCreateFreeInput() throws Exception {
        var body = new Body(); body.energy = 20_000_000; body.capacity = 2_000_000;
        var hook = input(body);
        var guard = hook.getClass().getMethod("dnv$rejectUnavailableInput", int.class, boolean.class, CallbackInfoReturnable.class);
        for (boolean simulate : new boolean[] {false, true}) {
            var callback = new CallbackInfoReturnable<Integer>("receive", true, -18_000_000);
            guard.invoke(hook, 100, simulate, callback);
            assertTrue(callback.isCancelled()); assertEquals(0, callback.getReturnValueI());
            assertEquals(20_000_000, body.energy);
        }
        body.energy = 1; body.world.isClientSide = true;
        var callback = new CallbackInfoReturnable<Integer>("receive", true, 100);
        guard.invoke(hook, 100, false, callback); assertEquals(0, callback.getReturnValueI());
        body.world.isClientSide = false; body.storage = false;
        callback = new CallbackInfoReturnable<>("receive", true, 100);
        guard.invoke(hook, 100, false, callback); assertEquals(0, callback.getReturnValueI());
    }
    private Object input(Body body) throws Exception {
        var hook = fixture("VehicleEnergyInputMixin", Set.of("dnv$budget", "dnv$autonomousInternalWrite",
                "dnv$limitReceive", "dnv$received", "dnv$rejectUnavailableInput", "dnv$excludeAutonomousExport", "dnv$excludeAutonomousExportCapability"), InputBody.class);
        ((InputBody) hook).vehicle = body; return hook;
    }
    private int limit(Object hook, int request) throws Exception {
        return (int) hook.getClass().getMethod("dnv$limitReceive", int.class).invoke(hook, request);
    }
    private void credit(Object hook, int actual, boolean simulate) throws Exception {
        hook.getClass().getMethod("dnv$received", int.class, boolean.class, CallbackInfoReturnable.class)
                .invoke(hook, actual, simulate, new CallbackInfoReturnable<Integer>("receive", false, actual));
    }
    private Object fixture(String name, Set<String> methods, Class<?> parent) throws Exception {
        String source = "com/liucodex/doggiesnewvehicle/compat/mixin/" + name;
        String target = "com/liucodex/doggiesnewvehicle/compat/gameplayprobe/" + name;
        var node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
        }
        node.access = Opcodes.ACC_PUBLIC; node.interfaces.clear(); node.innerClasses.clear(); node.fields.clear();
        node.superName = Type.getInternalName(parent);
        node.methods.removeIf(method -> !methods.contains(method.name));
        node.methods.forEach(method -> method.access = Opcodes.ACC_PUBLIC);
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
        var names = Map.of(source, target, "com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity", Type.getInternalName(Body.class),
                "net/minecraft/world/level/Level", Type.getInternalName(World.class),
                "net/minecraftforge/energy/IEnergyStorage", Type.getInternalName(BatteryPort.class));
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String name) { return names.getOrDefault(name, name); }
        }));
        return new ExactQueuedShotFixture.Loader(Map.of(target, writer.toByteArray())).loadClass(target.replace('/', '.')).getConstructor().newInstance();
    }
    public static class World { public long tick = 1; public boolean isClientSide; public long getGameTime() { return tick; } }
    public static class Body implements DnvEnergyInput {
        public World world = new World(); public DnvEnergyInputBudget budget = new DnvEnergyInputBudget(2048); public int energy, capacity = 2_000_000;
        public boolean storage = true;
        public int getEnergy() { return energy; } public int getMaxEnergy() { return capacity; }
        public boolean hasEnergyStorage() { return storage; }
        public World level() { return world; } public DnvEnergyInputBudget dnv$energyInputBudget() { return budget; }
    }
    public static class AutonomousBody extends Body implements DnvAutonomousEnergy {
        public boolean mutation; public boolean nativeEnergyMutation() { return mutation; }
    }
    public static class InventoryBody extends Body { }
    public static class InputBody { public Body vehicle; public Body getVehicle() { return vehicle; } }
    public interface BatteryPort { int extractEnergy(int request, boolean simulate); }
    public static class Battery implements BatteryPort {
        public int energy;
        public int extractEnergy(int request, boolean simulate) {
            int actual = Math.min(request, energy); if (!simulate) energy -= actual; return actual;
        }
    }
}
