package com.liucodex.doggiesnewvehicle.compat;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/** A resumable, server-thread-only walk over loaded entity sections. */
final class DnvTargetSectionPager<T> {
    static final int MAX_SECTION_LOOKUPS_PER_STEP = 64;
    static final int MAX_ENTITY_VISITS_PER_STEP = 64;

    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private final boolean splitZWindow;
    private int windowX;
    private int windowPart;
    private boolean windowReady;
    private long windowEnd;
    private long nextKey;
    private boolean hasCurrentSection;
    private long currentSection;
    private int entityOffset;
    private boolean complete;

    DnvTargetSectionPager(AABB bounds) {
        // Match EntitySectionStorage's query margin for entities wider than one section.
        minX = SectionPos.posToSectionCoord(bounds.minX - 2.0D);
        maxX = SectionPos.posToSectionCoord(bounds.maxX + 2.0D);
        minY = SectionPos.posToSectionCoord(bounds.minY - 4.0D);
        maxY = SectionPos.posToSectionCoord(bounds.maxY);
        minZ = SectionPos.posToSectionCoord(bounds.minZ - 2.0D);
        maxZ = SectionPos.posToSectionCoord(bounds.maxZ + 2.0D);
        // SectionPos stores signed X in the high bits, then unsigned packed Z.
        // A Z range straddling zero has two sorted-key windows per X column.
        splitZWindow = minZ < 0 && maxZ >= 0;
        windowX = minX;
    }

    Step step(LongSortedSet sectionKeys, SectionReader<T> reader, Consumer<T> visit) {
        if (complete) return new Step(0, 0, true);
        int lookups = 0;
        int visits = 0;
        while (lookups < MAX_SECTION_LOOKUPS_PER_STEP && visits < MAX_ENTITY_VISITS_PER_STEP) {
            if (!hasCurrentSection) {
                if (!windowReady && !prepareWindow()) break;
                if (nextKey > windowEnd) {
                    advanceWindow();
                    continue;
                }
                // Count empty-window seeks too; a sparse level still has bounded work.
                lookups++;
                LongBidirectionalIterator keys = sectionKeys.tailSet(nextKey).iterator();
                if (!keys.hasNext()) {
                    advanceWindow();
                    continue;
                }
                long found = keys.nextLong();
                if (found > windowEnd) {
                    advanceWindow();
                    continue;
                }
                currentSection = found;
                hasCurrentSection = true;
                entityOffset = 0;
            } else if (lookups == 0) {
                // The section was only partially visited in the previous tick.
                lookups++;
            }
            List<T> entities = withinYBounds(currentSection)
                    ? reader.entities(currentSection) : null;
            if (entities == null || entities.isEmpty()) {
                finishSection();
                continue;
            }
            while (entityOffset < entities.size() && visits < MAX_ENTITY_VISITS_PER_STEP) {
                visit.accept(entities.get(entityOffset++));
                visits++;
            }
            if (entityOffset >= entities.size()) finishSection();
        }
        return new Step(lookups, visits, complete);
    }

    private boolean prepareWindow() {
        if (windowX > maxX) {
            complete = true;
            return false;
        }
        int fromZ = splitZWindow && windowPart == 0 ? 0 : minZ;
        int toZ = splitZWindow && windowPart == 0 ? maxZ
                : splitZWindow ? -1 : maxZ;
        nextKey = SectionPos.asLong(windowX, 0, fromZ);
        windowEnd = SectionPos.asLong(windowX, 0, toZ) | ((1L << 20) - 1L);
        windowReady = true;
        return true;
    }

    private void advanceWindow() {
        windowReady = false;
        if (splitZWindow && windowPart == 0) windowPart = 1;
        else {
            windowPart = 0;
            windowX++;
        }
    }

    private void finishSection() {
        hasCurrentSection = false;
        if (currentSection >= windowEnd) advanceWindow();
        else nextKey = currentSection + 1L;
    }

    private boolean withinYBounds(long key) {
        int y = SectionPos.y(key);
        return y >= minY && y <= maxY;
    }

    interface SectionReader<T> {
        @Nullable List<T> entities(long key);
    }

    record Step(int sectionLookups, int entityVisits, boolean complete) {}
}
