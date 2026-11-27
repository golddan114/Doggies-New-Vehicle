package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Runs the compiled station read projection; native ammunition remains an explicit read-only port. */
class DnvRemoteAmmoGuideExecutionTest {
    @Test void allThreeRemoteHeavyTurretsReportTheirInstalledAmmoAndUseTheTurretInventoryGesture() throws Exception {
        var type = fixture();
        var names = type.getMethod("ammoNames", Crew.class);
        var guide = type.getMethod("ammoGuide", Crew.class);
        for (String model : List.of("dragon_flak88", "m1_yunlong", "dragon_flak40")) {
            var input = JsonParser.parseString(Files.readString(Path.of("src/artillery/resources/data/doggievehicle/sbw/vehicles",model+".json")))
                .getAsJsonObject().getAsJsonObject("Weapons").getAsJsonObject("Main").getAsJsonArray("AmmoType");
            var turret = new Turret();
            var expected = new ArrayList<String>();
            for (var entry : input) {
                String id = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("Ammo").getAsString();
                expected.add(id);turret.gun.consumers.add(new Consumer(new Stack(id)));
            }
            Crew target = () -> turret;
            assertEquals(String.join(" / ",expected),((Component)names.invoke(null,target)).getString());
            var message = (TranslatableContents)((Component)guide.invoke(null,target)).getContents();
            assertEquals("message.doggiesnewvehicle.m1_crew.ammo_guide",message.getKey());
            assertEquals(turret.getDisplayName(),message.getArgs()[0]);
            assertEquals(String.join(" / ",expected),((Component)message.getArgs()[1]).getString());
            // Datapack/config changes appear on the next observation, without a second ammo cache or selection write.
            turret.gun.consumers = new ArrayList<>(List.of(new Consumer(new Stack("替换弹药"))));
            assertEquals("替换弹药",((Component)names.invoke(null,target)).getString());
            turret.gun.consumers.clear();
            assertEquals("message.doggiesnewvehicle.m1_crew.ammo_unknown",
                ((TranslatableContents)((Component)names.invoke(null,target)).getContents()).getKey());
        }
    }
    @Test void failedOrMissingWeaponDataHasAnExplicitHintWithoutInventingAnItem() throws Exception {
        var type=fixture();var method=type.getMethod("ammoNames",Crew.class);
        var turret=new Turret();turret.gun=null;
        for(Crew target:List.<Crew>of(()->turret,Body::new))
            assertEquals("message.doggiesnewvehicle.m1_crew.ammo_unknown",
                ((TranslatableContents)((Component)method.invoke(null,target)).getContents()).getKey());
    }
    private Class<?> fixture() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/api/combat/DnvNativeCrewTarget",Crew.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity",Turret.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/GunData",Gun.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/GunProp",Prop.class);
        NuclearRecoveryExecutionTest.map(names,"com/atsuishio/superbwarfare/data/gun/AmmoConsumer",Consumer.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/item/ItemStack",Stack.class);
        return NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/DnvM1RemoteCrew",
            Set.of("ammoNames","ammoGuide"),names,null,false);
    }
    public interface Crew { Body remoteCrewEntity(); }
    public static class Body { public Component getDisplayName(){return Component.literal("被绑定炮塔");} }
    public static class Turret extends Body { public Gun gun=new Gun();public Gun getGunData(String name){assertEquals("Main",name);return gun;} }
    public static class Prop { public static final Prop AMMO_CONSUMER=new Prop(); }
    public static class Gun { public List<Consumer> consumers=new ArrayList<>();public Object get(Prop prop){assertSame(Prop.AMMO_CONSUMER,prop);return consumers;} }
    public record Consumer(Stack value) { public Stack stack(){return value;} }
    public record Stack(String name) { public boolean isEmpty(){return name.isEmpty();}public Component getHoverName(){return Component.literal(name);} }
}
