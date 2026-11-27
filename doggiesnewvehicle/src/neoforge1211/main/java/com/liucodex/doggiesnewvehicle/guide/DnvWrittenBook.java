package com.liucodex.doggiesnewvehicle.guide;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** NeoForge 1.21.1 vanilla data-component adapter for the same authored guide. */
public final class DnvWrittenBook {
    private DnvWrittenBook() { }
    public static ItemStack create(String language) {
        var book = DnvGuideBooks.book(language);
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        var pages = book.pages().stream().map(p -> Filterable.<Component>passThrough(p.copy())).toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(book.title()), "DNV", 0, pages, true));
        return stack;
    }
}
