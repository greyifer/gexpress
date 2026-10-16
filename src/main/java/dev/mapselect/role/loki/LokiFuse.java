package dev.mapselect.role.loki;
import java.util.UUID;
/** A transfer changes only the holder and previous giver, never the expiry. */
public record LokiFuse(UUID caster,UUID holder,UUID previous,long expires){
    public boolean canPassTo(UUID target){return !holder.equals(target)&&!target.equals(previous);}
    public LokiFuse passTo(UUID target){if(!canPassTo(target))throw new IllegalArgumentException("Cannot pass directly back");return new LokiFuse(caster,target,holder,expires);}
}
