package dev.mapselect.role.counterfeiter;

import java.util.*;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/** Exactly one captured non-owner is truthful; every other entry excludes its actual role. */
public final class CounterfeitReportPolicy {
    private CounterfeitReportPolicy(){}
    public static <T> Map<UUID,T> generate(Collection<UUID> captured,UUID owner,Function<UUID,T> actual,List<T> pool,RandomGenerator random){
        var roles=new LinkedHashMap<UUID,T>();
        for(UUID id:captured){T role=actual.apply(id);if(role!=null)roles.put(id,role);}
        var eligible=roles.keySet().stream().filter(id->!id.equals(owner)).toList();
        if(eligible.isEmpty())return Map.of();
        UUID truthful=eligible.get(random.nextInt(eligible.size()));
        var result=new LinkedHashMap<UUID,T>();
        for(var entry:roles.entrySet()){
            if(entry.getKey().equals(truthful)){result.put(entry.getKey(),entry.getValue());continue;}
            var alternatives=pool.stream().filter(Objects::nonNull).filter(role->!role.equals(entry.getValue())).distinct().toList();
            if(alternatives.isEmpty())throw new IllegalArgumentException("A false report requires an alternative role");
            result.put(entry.getKey(),alternatives.get(random.nextInt(alternatives.size())));
        }
        return result;
    }
}
