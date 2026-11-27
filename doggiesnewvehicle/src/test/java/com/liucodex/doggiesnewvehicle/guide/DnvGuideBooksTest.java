package com.liucodex.doggiesnewvehicle.guide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.junit.jupiter.api.Test;

class DnvGuideBooksTest {
    private List<Component> tree(Component page) {
        var result=new ArrayList<Component>();result.add(page);
        for(var child:page.getSiblings())result.addAll(tree(child));
        return result;
    }
    @Test void bothEditionsHaveEveryChapterAndFitVanillaPageAndLineLimits() {
        var chinese=DnvGuideBooks.book("zh_cn");var english=DnvGuideBooks.book("en_us");
        assertEquals(chinese.chapters().keySet(),english.chapters().keySet());
        assertEquals(29,chinese.chapters().size());
        assertTrue(chinese.chapters().keySet().containsAll(List.of("libertad", "yue", "feiying")));
        for(var book:List.of(chinese,english)){
            assertTrue(book.title().length()<=32);assertTrue(book.pages().size()>26&&book.pages().size()<=100);
            for(var page:book.pages()){
                String text=page.getString();assertTrue(text.split("\n",-1).length<=14,text);
                for(String line:text.split("\n"))if(!line.startsWith("key."))assertTrue(DnvGuideBooks.width(line)<=114,line);
                assertFalse(text.contains("[["));assertFalse(text.contains("\uFFFD"));
            }
            System.out.println(book.title()+": "+book.pages().size()+" pages");
        }
    }
    @Test void everyContentsLinkReachesItsChapterAndEveryBodyPageReturnsToItsContents() {
        for(String language:List.of("zh_cn","en_us")){
            var book=DnvGuideBooks.book(language);var starts=new HashSet<>(book.chapters().values());
            var reached=new HashSet<Integer>();
            for(int index=0;index<book.pages().size();index++){
                var links=tree(book.pages().get(index)).stream().map(c->c.getStyle().getClickEvent()).filter(Objects::nonNull).toList();
                assertFalse(links.isEmpty());
                for(var link:links){
                    assertEquals(ClickEvent.Action.CHANGE_PAGE,link.getAction());int target=Integer.parseInt(link.getValue());
                    assertTrue(target>0&&target<=book.pages().size());
                    if(index<3){assertTrue(starts.contains(target));reached.add(target);}
                    else assertTrue(target<=3);
                }
                if(index>=3)assertEquals(1,links.size());
            }
            assertEquals(starts,reached);
        }
    }
    @Test void controlsStayAsLiveKeybindComponentsAndUnknownLanguageFailsClearly() {
        for(String language:List.of("zh_cn","en_us")){
            var keys=DnvGuideBooks.book(language).pages().stream().flatMap(p->tree(p).stream())
                    .map(Component::getContents).filter(KeybindContents.class::isInstance).map(KeybindContents.class::cast)
                    .map(KeybindContents::getName).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("key.superbwarfare.fire","key.superbwarfare.reload","key.superbwarfare.interact",
                    "key.superbwarfare.dismount","key.superbwarfare.change_ammo_forward","key.doggiesnewvehicle.blizzard_mode",
                    "key.libertad.secondaries","key.libertad.watch_camera","key.dnvcarriercube.panel","key.dnvcarriercube.flight_mode",
                    "key.dnvcarriercube.return_to_carrier"),keys);
        }
        assertThrows(IllegalArgumentException.class,()->DnvGuideBooks.book("missing"));
    }
}
