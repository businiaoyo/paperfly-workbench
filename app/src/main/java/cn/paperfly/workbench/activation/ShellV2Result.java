package cn.paperfly.workbench.activation;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded ADB shell-v2 result decoder. Raw sessions avoid a terminal hangup killing the helper. */
final class ShellV2Result {
    static String read(InputStream input)throws IOException{
        DataInputStream in=new DataInputStream(input);ByteArrayOutputStream output=new ByteArrayOutputStream();
        for(;;){
            int channel=in.read();if(channel<0)throw new EOFException("启动会话在返回退出状态前断开");
            byte[] header=new byte[4];in.readFully(header);
            long size=(header[0]&255L)|((header[1]&255L)<<8)|((header[2]&255L)<<16)|((header[3]&255L)<<24);
            if(size>16384||output.size()+size>16384)throw new IOException("启动响应过长");
            byte[] payload=new byte[(int)size];in.readFully(payload);
            if(channel==1||channel==2)output.write(payload);
            else if(channel==3){
                if(payload.length!=1&&payload.length!=4)throw new IOException("启动退出状态无效");
                int code=payload[0]&255;for(int i=1;i<payload.length;i++)code|=(payload[i]&255)<<(8*i);
                if(code!=0)throw new IOException("本机启动命令未完成（"+code+"）");return output.toString(StandardCharsets.UTF_8.name());
            }else throw new IOException("启动响应通道无效");
        }
    }
}
