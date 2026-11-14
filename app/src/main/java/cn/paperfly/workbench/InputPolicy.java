package cn.paperfly.workbench;

/** Only text/navigation/modifier keys may be routed to an owned app; system keys stay on the host. */
final class InputPolicy {
    private InputPolicy(){}
    static boolean allowedKey(int code){return code>=7&&code<=23||code>=29&&code<=77||code==92||code==93||code>=111&&code<=124||code>=131&&code<=163||code==204||code>=211&&code<=218;}
}
