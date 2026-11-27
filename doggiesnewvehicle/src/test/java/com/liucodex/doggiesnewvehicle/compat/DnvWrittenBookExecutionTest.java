package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes the platform's actual book adapter; only registry-backed item/component carriers are ported. */
class DnvWrittenBookExecutionTest {
    @Test void nativeBookCarrierReceivesCompleteIndependentLanguageStacks() throws Exception {
        String source="com/liucodex/doggiesnewvehicle/guide/DnvWrittenBook",target=source+"Probe";
        var names=new HashMap<String,String>();names.put(source,target);
        Map.of("world/item/ItemStack",Stack.class,"world/item/Items",Catalog.class,"world/item/Item",Item.class,
                "world/level/ItemLike",Item.class,"core/component/DataComponents",Components.class,
                "core/component/DataComponentType",Kind.class,"world/item/component/WrittenBookContent",Content.class,
                "server/network/Filterable",Filtered.class).forEach((n,c)->names.put("net/minecraft/"+n,Type.getInternalName(c)));
        var writer=new ClassWriter(0);
        try(var in=getClass().getResourceAsStream("/"+source+".class")){
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer,new Remapper(){
                @Override public String map(String key){return names.getOrDefault(key,key);}
            }),0);
        }
        var type=new ExactQueuedShotFixture.Loader(Map.of(target,writer.toByteArray())).loadClass(target.replace('/','.'));
        Stack previous=null;
        for(String language:List.of("zh_cn","en_us")){
            var expected=com.liucodex.doggiesnewvehicle.guide.DnvGuideBooks.book(language);
            var stack=(Stack)type.getMethod("create",String.class).invoke(null,language);assertNotSame(previous,stack);previous=stack;
            assertSame(Catalog.WRITTEN_BOOK,stack.item);
            if(stack.tag!=null){
                assertEquals(expected.title(),stack.tag.getString("title"));assertEquals("DNV",stack.tag.getString("author"));
                assertTrue(stack.tag.getBoolean("resolved"));assertEquals(0,stack.tag.getInt("generation"));
                var pages=stack.tag.getList("pages",Tag.TAG_STRING);assertEquals(expected.pages().size(),pages.size());
                for(int n=0;n<pages.size();n++){
                    var json=com.google.gson.JsonParser.parseString(pages.getString(n));assertTrue(json.isJsonObject());
                    assertTrue(pages.getString(n).length()<32767);assertTrue(pages.getString(n).contains("change_page"));
                }
                var saved=stack.tag.copy();stack.tag.putString("title","changed");assertEquals(expected.title(),saved.getString("title"));
            }else{
                var content=stack.content;assertNotNull(content);assertSame(Components.WRITTEN_BOOK_CONTENT,stack.kind);
                assertEquals(expected.title(),content.title.raw);assertEquals("DNV",content.author);assertTrue(content.resolved);
                assertEquals(0,content.generation);assertEquals(expected.pages().size(),content.pages.size());
                for(int n=0;n<content.pages.size();n++){
                    var page=(Component)content.pages.get(n).raw;assertEquals(expected.pages().get(n),page);assertNotSame(expected.pages().get(n),page);
                }
            }
        }
    }
    public static class Item { }
    public static class Catalog {public static final Item WRITTEN_BOOK=new Item();}
    public static class Kind { }
    public static class Components {public static final Kind WRITTEN_BOOK_CONTENT=new Kind();}
    public static class Filtered<T> {
        public final T raw;Filtered(T raw){this.raw=raw;}public static <T> Filtered<T> passThrough(T raw){return new Filtered<>(raw);}
    }
    public static class Content {
        final Filtered<?> title;final String author;final int generation;final List<Filtered<?>> pages;final boolean resolved;
        public Content(Filtered<?> title,String author,int generation,List<Filtered<?>> pages,boolean resolved){
            this.title=title;this.author=author;this.generation=generation;this.pages=pages;this.resolved=resolved;
        }
    }
    public static class Stack {
        final Item item;CompoundTag tag;Content content;Kind kind;
        public Stack(Item item){this.item=item;}
        public void setTag(CompoundTag tag){this.tag=tag;}
        public Object set(Kind kind,Object content){this.kind=kind;this.content=(Content)content;return null;}
    }
}
