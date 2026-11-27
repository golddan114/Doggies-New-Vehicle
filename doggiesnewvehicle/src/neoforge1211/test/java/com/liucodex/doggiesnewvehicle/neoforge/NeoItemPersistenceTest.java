package com.liucodex.doggiesnewvehicle.neoforge;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** In-memory vanilla registries and item codecs only; no client/server/world is started. */
class NeoItemPersistenceTest {
    @BeforeAll static void bootstrapItemCodecs() {
        // Empty discovery context supplies NeoForge's feature-flag loader; it loads no mods.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @Test void bindingEditsPersistAndReadsCannotSilentlyMutateTheItem() {
        var stack = new ItemStack(Items.STICK);
        assertNull(DnvNeoItemData.read(stack));
        UUID original = UUID.randomUUID();
        DnvNeoItemData.update(stack, tag -> tag.putUUID("Owner", original));
        DnvNeoItemData.update(stack, tag -> tag.putBoolean("Hold", true));
        CompoundTag view = DnvNeoItemData.read(stack);
        assertEquals(original, view.getUUID("Owner"));
        assertTrue(view.getBoolean("Hold"));
        view.remove("Owner");
        assertEquals(original, DnvNeoItemData.read(stack).getUUID("Owner"));
        DnvNeoItemData.update(stack, tag -> tag.remove("Hold"));
        assertFalse(DnvNeoItemData.read(stack).contains("Hold"));
        assertEquals(original, DnvNeoItemData.read(stack).getUUID("Owner"));
    }
    @Test void savedStackRestoresCustomDataWithoutASecondStateOwner() {
        var stack = new ItemStack(Items.STICK);
        var tag = new CompoundTag(); tag.putInt("SchemaVersion", 1); tag.putString("Vehicle", "doggievehicle:apocalypse");
        DnvNeoItemData.set(stack, tag);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var encoded = stack.save(registries);
        var restored = ItemStack.parse(registries, encoded).orElseThrow();
        assertEquals(tag, DnvNeoItemData.read(restored));
        tag.putInt("SchemaVersion", 99);
        assertEquals(1, DnvNeoItemData.read(stack).getInt("SchemaVersion"));
        assertEquals(1, DnvNeoItemData.read(restored).getInt("SchemaVersion"));
    }
}
