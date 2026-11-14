package cn.paperfly.workbench.activation;

import java.util.*;

/** Session-only NSD candidates. Callers verify local addresses and device hints before publishing. */
final class AdbEndpointCatalog {
    static final class Endpoint {
        final String service;final int port;
        Endpoint(String service,int port){this.service=service;this.port=port;}
        boolean matches(String pairedGuid){
            if(pairedGuid==null||service.equalsIgnoreCase(pairedGuid))return true;
            // Android NSD renames a conflicting service, but pairing keeps the original GUID.
            if(service.length()<=pairedGuid.length()||!service.regionMatches(true,0,pairedGuid,0,pairedGuid.length()))return false;
            return service.substring(pairedGuid.length()).matches(" \\([1-9][0-9]{0,8}\\)");
        }
    }
    private static final class Entry {
        final long token;
        final ArrayDeque<Integer> ports=new ArrayDeque<>();
        Entry(long token){this.token=token;}
    }
    private final Map<String,Entry> services=new LinkedHashMap<>();
    private long nextToken;

    static int nextPort(Endpoint[] endpoints,String pairedGuid,Set<Integer> attempted){
        for(Endpoint endpoint:endpoints)if(endpoint.matches(pairedGuid)&&!attempted.contains(endpoint.port))return endpoint.port;
        return 0;
    }

    static String key(String type,String name){
        String normalized=type.toLowerCase(Locale.ROOT).replaceAll("\\.+$","");
        if(normalized.endsWith(".local"))normalized=normalized.substring(0,normalized.length()-6);
        return normalized+"/"+name.toLowerCase(Locale.ROOT);
    }
    long found(String key){
        Entry entry=services.get(key);
        if(entry==null){entry=new Entry(++nextToken);services.put(key,entry);}
        return entry.token;
    }
    void update(String key,long token,int port,boolean local){
        Entry entry=services.get(key);
        if(entry==null||entry.token!=token)return;
        if(!local||port<1||port>65535){entry.ports.clear();return;}
        entry.ports.remove(port);entry.ports.addFirst(port);
        // Conflicting announcements can briefly alternate ports for the same local service.
        // Keep a bounded set for authenticated connection retries, never a port scan.
        while(entry.ports.size()>3)entry.ports.removeLast();
    }
    void lost(String key){services.remove(key);}
    void clear(){services.clear();}
    Endpoint[] connections(){
        String prefix="_adb-tls-connect._tcp/";List<Endpoint> result=new ArrayList<>();
        for(Map.Entry<String,Entry> item:services.entrySet())if(item.getKey().startsWith(prefix))
            for(int port:item.getValue().ports)result.add(new Endpoint(item.getKey().substring(prefix.length()),port));
        return result.toArray(new Endpoint[0]);
    }
    int[] ports(boolean pairing){
        String prefix=pairing?"_adb-tls-pairing._tcp/":"_adb-tls-connect._tcp/";
        Set<Integer> ports=new LinkedHashSet<>();
        for(Map.Entry<String,Entry> item:services.entrySet())if(item.getKey().startsWith(prefix)){
            if(pairing){if(!item.getValue().ports.isEmpty())ports.add(item.getValue().ports.peekFirst());}
            else ports.addAll(item.getValue().ports);
        }
        return ports.stream().mapToInt(Integer::intValue).toArray();
    }
}
