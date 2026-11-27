package com.liucodex.carriercube;

import net.minecraft.world.level.ChunkPos;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read only the 4 KiB location tables. Minecraft's own IOWorker reads chunk NBT. */
final class CubeSavedRegions implements Iterator<ChunkPos> {
    private record Region(int x,int z,BitSet present) {}
    private static final Pattern NAME=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final List<Region> regions;
    private final int total;
    int total(){return total;}
    private int region, index;
    private CubeSavedRegions(List<Region> regions) {this.regions=regions;total=regions.stream().mapToInt(r->r.present.cardinality()).sum();}
    static CubeSavedRegions open(Path folder,ChunkPos center) {
        var regions=new ArrayList<Region>();
        if(Files.isDirectory(folder)) try(var paths=Files.newDirectoryStream(folder,"r.*.*.mca")) {
            var header=ByteBuffer.allocate(4096);
            for(Path file:paths) {
                var match=NAME.matcher(file.getFileName().toString());
                if(!match.matches())continue;
                header.clear();
                try(var channel=FileChannel.open(file,StandardOpenOption.READ)) {
                    while(header.hasRemaining() && channel.read(header)>0) {}
                } catch(NoSuchFileException disappeared) { continue; }
                if(header.position()!=4096)continue;
                header.flip();
                var bits=new BitSet(1024);
                for(int i=0;i<1024;i++)if((header.getInt()>>>8)>=2)bits.set(i);
                regions.add(new Region(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),bits));
            }
        } catch(IOException e) {throw new java.io.UncheckedIOException(e);}
        regions.sort(Comparator.comparingDouble(r->Math.hypot(r.x*32.0+16-center.x,r.z*32.0+16-center.z)));
        return new CubeSavedRegions(regions);
    }
    public boolean hasNext() {
        while(region<regions.size()) {
            int bit=regions.get(region).present.nextSetBit(index);
            if(bit>=0) {index=bit;return true;}
            index=0;region++;
        }
        return false;
    }
    public ChunkPos next() {
        if(!hasNext())throw new NoSuchElementException();
        Region r=regions.get(region);int bit=index++;
        return new ChunkPos(r.x*32+(bit&31),r.z*32+(bit>>5));
    }
}
