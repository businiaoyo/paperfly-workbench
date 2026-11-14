package cn.paperfly.workbench;

/** Analytic critically damped motion. Retargeting retains position and velocity. */
final class StageMotion {
    double value,velocity,target;
    StageMotion(double value){snap(value);}
    void snap(double value){this.value=value;target=value;velocity=0;}
    void target(double value){target=value;}
    boolean step(double seconds){
        double dt=Math.max(0,Math.min(.05,seconds)),omega=26;
        double error=value-target,temp=(velocity+omega*error)*dt,decay=Math.exp(-omega*dt);
        value=target+(error+temp)*decay;velocity=(velocity-omega*temp)*decay;
        if(Math.abs(value-target)<.2&&Math.abs(velocity)<3){value=target;velocity=0;return false;}
        return true;
    }
}
