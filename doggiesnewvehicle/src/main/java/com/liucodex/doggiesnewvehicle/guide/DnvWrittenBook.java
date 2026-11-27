package com.liucodex.doggiesnewvehicle.guide;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Forge 1.20.1 vanilla written-book format. No custom screen, item or dependency. */
public final class DnvWrittenBook {
    private DnvWrittenBook() { }
    public static ItemStack create(String language) {
        var book = DnvGuideBooks.book(language);
        var stack = new ItemStack(Items.WRITTEN_BOOK); var tag = new CompoundTag();
        var pages = new ListTag();
        for (var page : book.pages()) pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        tag.putString("title", book.title()); tag.putString("author", "DNV");
        tag.putInt("generation", 0); tag.putBoolean("resolved", true); tag.put("pages", pages);
        stack.setTag(tag);
        return stack;
    }
}
