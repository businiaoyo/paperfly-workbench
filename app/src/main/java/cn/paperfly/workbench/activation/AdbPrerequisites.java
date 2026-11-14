package cn.paperfly.workbench.activation;

/** Manufacturer-specific guidance only; never changes system settings or disables authentication. */
final class AdbPrerequisites {
    static boolean needsChargingGuide(String manufacturer){return "HONOR".equalsIgnoreCase(manufacturer)||"HUAWEI".equalsIgnoreCase(manufacturer);}
    static String connectionHint(String manufacturer,int usb,int wireless,int charging){
        if(wireless==0)return "系统无线调试已关闭，请开启后点“已配对，直接激活”。";
        if(needsChargingGuide(manufacturer)&&(usb==0||charging==0))
            return "配对信息已保留。请在开发者选项开启“仅充电模式下允许 ADB 调试”和“USB 调试”，再关闭并重新开启“无线调试”，返回点“已配对，直接激活”。无需连接电脑或重复配对。";
        return "配对信息已保留，但尚未发现本机连接端口。请关闭并重新开启无线调试，再点“已配对，直接激活”；也可填写系统显示的连接端口。";
    }
}
