package dev.mapselect.role.loki;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic replacement keeps a speaker in at most one reciprocal pair. */
public final class LokiVoicePairs {
    private record Link(UUID partner, long until) {}
    private final Map<UUID,Link> links = new ConcurrentHashMap<>();
    public synchronized void swap(UUID first,UUID second,long until) {
        if (first.equals(second)) throw new IllegalArgumentException("Distinct voice targets required");
        remove(first); remove(second); links.put(first,new Link(second,until)); links.put(second,new Link(first,until));
    }
    public synchronized UUID partner(UUID player,long now) {
        Link link=links.get(player);
        if (link==null) return null;
        if (now>=link.until) { remove(player); return null; }
        return link.partner;
    }
    public synchronized void remove(UUID player) {
        Link old=links.remove(player);
        if (old!=null) {
            Link reverse=links.get(old.partner);
            if (reverse!=null && reverse.partner.equals(player)) links.remove(old.partner);
        }
    }
    public synchronized void clear() { links.clear(); }
}
