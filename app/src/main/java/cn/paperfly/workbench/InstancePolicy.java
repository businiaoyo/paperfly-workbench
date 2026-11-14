package cn.paperfly.workbench;
/** Manifest launch modes that permit a distinct task without stealing an existing singleton. */
final class InstancePolicy {
    static boolean supports(int mode){return mode==0||mode==1||mode==4;}
}
