package cn.paperfly.workbench.activation;

/** Input grammar shared by the page and notification reply. Pairing codes are never persisted. */
public final class PairingInput {
    public final int port;
    public final String code;
    private PairingInput(int port,String code){this.port=port;this.code=code;}
    public static int port(String value){
        if(value==null||value.trim().isEmpty())return 0;
        String text=value.trim();if(!text.matches("[0-9]{1,5}"))throw new IllegalArgumentException("请输入 1–65535 的端口号");
        int port=Integer.parseInt(text);if(port<1||port>65535)throw new IllegalArgumentException("端口号应为 1–65535");return port;
    }
    public static PairingInput parse(String input,int defaultPort){
        String[] words=input==null?new String[0]:input.trim().split("[\\s:：,，]+");
        int port=defaultPort;String code;
        if(words.length==1)code=words[0];else if(words.length==2){port=port(words[0]);code=words[1];}
        else throw new IllegalArgumentException("请输入 6 位配对码，或“配对端口 配对码”");
        if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("配对码需要 6 位数字");
        if(port<0||port>65535)throw new IllegalArgumentException("配对端口无效");return new PairingInput(port,code);
    }
}
