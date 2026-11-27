package com.liucodex.doggiesnewvehicle.client;

import java.util.TreeMap;
import java.util.function.Consumer;

/** Client-thread event registry; bounded discovery never retains a mutable world's iterator. */
final class ClientEntityCycle<T> {
    private final TreeMap<Integer,T> loaded = new TreeMap<>();
    private Integer cursor;

    void put(int id,T entity) { loaded.put(id,entity); }
    void remove(int id,T entity) { loaded.remove(id,entity); }
    void clear() { loaded.clear();cursor=null; }
    void visit(int budget,Consumer<T> visitor) {
        int remaining=Math.min(Math.max(0,budget),loaded.size());
        while(remaining-->0&&!loaded.isEmpty()) {
            var next=cursor==null?loaded.firstEntry():loaded.higherEntry(cursor);
            if(next==null)next=loaded.firstEntry();
            cursor=next.getKey();visitor.accept(next.getValue());
        }
    }
}
