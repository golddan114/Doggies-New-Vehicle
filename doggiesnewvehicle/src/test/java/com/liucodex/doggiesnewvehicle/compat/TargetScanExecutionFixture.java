package com.liucodex.doggiesnewvehicle.compat;

import com.liucodex.doggiesnewvehicle.compat.ApocalypseAutonomyExecutionTest.Body;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.*;
import net.minecraft.core.SectionPos;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Executes real compiled cache/pager/budgets; only entity storage and world access are ports. */
public final class TargetScanExecutionFixture {
    private static final String BASE="com/liucodex/doggiesnewvehicle/compat/";
    private static final List<String> CLASSES=List.of("DnvTargetCandidateCache", "DnvTargetCandidateCache$ScanState",
            "DnvTargetCandidateCache$RankedId", "DnvTargetSectionPager", "DnvTargetSectionPager$Step",
            "DnvTargetSectionPager$SectionReader", "DnvTargetingBudget", "DnvTargetingBudget$ScanLedger",
            "DnvTargetingBudget$ScanLedger$Owner");

    static void configure(Map<String,String> names) {
        for (String name:CLASSES) names.put(BASE+name, BASE+"executionprobe/"+name);
        Map.ofEntries(
                Map.entry("net/minecraft/world/level/entity/LevelEntityGetter",Getter.class),
                Map.entry("net/minecraft/world/level/entity/PersistentEntitySectionManager",Manager.class),
                Map.entry("net/minecraft/world/level/entity/EntitySectionStorage",Storage.class),
                Map.entry("net/minecraft/world/level/entity/EntitySection",Section.class),
                Map.entry("net/minecraft/world/level/entity/Visibility",Visibility.class),
                Map.entry("net/minecraft/util/ClassInstanceMultiMap",EntityList.class),
                Map.entry(BASE+"mixin/DnvServerLevelEntityManagerAccess",ServerAccess.class),
                Map.entry(BASE+"mixin/DnvPersistentEntitySectionsAccess",ManagerAccess.class),
                Map.entry(BASE+"mixin/DnvEntitySectionKeysAccess",StorageAccess.class),
                Map.entry(BASE+"mixin/DnvEntitySectionEntitiesAccess",SectionAccess.class)
        ).forEach((key,type)->names.put(key,Type.getInternalName(type)));
    }

    static Map<String,byte[]> classes(Map<String,String> names) throws Exception {
        Map<String,byte[]> output=new HashMap<>();
        for(String name:CLASSES) {
            ClassWriter writer=new ClassWriter(0);
            try(var stream=TargetScanExecutionFixture.class.getResourceAsStream("/"+BASE+name+".class")) {
                new ClassReader(Objects.requireNonNull(stream).readAllBytes()).accept(new ClassRemapper(writer,new Remapper(){
                    @Override public String map(String value){return names.getOrDefault(value,value);}
                }),0);
            }
            output.put(names.get(BASE+name),writer.toByteArray());
        }
        return output;
    }

    public interface Getter {}
    public interface ServerAccess { Manager dnv$getEntityManager(); }
    public interface ManagerAccess { Storage dnv$getSectionStorage(); }
    /** Public getter wrapper intentionally does not inherit the vanilla accessor interface. */
    public record WrappedGetter(Getter delegate) implements Getter {}
    public interface StorageAccess { LongSortedSet dnv$getSectionIds(); }
    public interface SectionAccess { EntityList dnv$getStorage(); }
    public static final class GetterImpl implements Getter {
        public final Storage storage=new Storage();
    }
    public static final class Manager implements ManagerAccess {
        final Storage storage;
        public Manager(Storage storage){this.storage=storage;}
        public Storage dnv$getSectionStorage(){return storage;}
    }
    public static final class Storage implements StorageAccess {
        final Map<Long,Section> sections=new HashMap<>();
        final LongSortedSet keys=new LongAVLTreeSet();
        int visits;
        void rebuild(Collection<Body> entities) {
            sections.clear(); keys.clear(); visits=0;
            for(Body body:entities) {
                long key=SectionPos.asLong(SectionPos.posToSectionCoord(body.position.x),
                        SectionPos.posToSectionCoord(body.position.y),SectionPos.posToSectionCoord(body.position.z));
                keys.add(key); sections.computeIfAbsent(key,ignored->new Section(this)).values.values.add(body);
            }
        }
        public Section getSection(long key){return sections.get(key);}
        public LongSortedSet dnv$getSectionIds(){return keys;}
    }
    public static final class Section implements SectionAccess {
        final EntityList values;
        Section(Storage storage){values=new EntityList(storage);}
        public Visibility getStatus(){return new Visibility();}
        public EntityList dnv$getStorage(){return values;}
    }
    public static final class Visibility { public boolean isAccessible(){return true;} }
    public static final class EntityList {
        final List<Body> values=new ArrayList<>();
        final Storage storage;
        final List<Body> observed=new AbstractList<>() {
            @Override public Body get(int index){storage.visits++;return values.get(index);}
            @Override public int size(){return values.size();}
        };
        EntityList(Storage storage){this.storage=storage;}
        public List<Body> getAllInstances(){return observed;}
    }
}
