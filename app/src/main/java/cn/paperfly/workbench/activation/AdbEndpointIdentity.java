package cn.paperfly.workbench.activation;

import java.math.BigDecimal;

/** Discovery hints can exclude another device, but never replace loopback or ADB authentication. */
final class AdbEndpointIdentity {
    private AdbEndpointIdentity(){}
    static boolean local(boolean localAddress,String deviceModel,int deviceApi,String advertisedModel,String advertisedApi){
        if(!localAddress)return false;
        if(advertisedModel!=null&&!advertisedModel.trim().isEmpty()
                &&!advertisedModel.trim().equalsIgnoreCase(deviceModel))return false;
        if(advertisedApi!=null&&!advertisedApi.trim().isEmpty())try{
            if(advertisedApi.length()>16||new BigDecimal(advertisedApi.trim()).intValueExact()!=deviceApi)return false;
        }catch(ArithmeticException|NumberFormatException error){return false;}
        return true;
    }
}
