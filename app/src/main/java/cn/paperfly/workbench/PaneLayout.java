package cn.paperfly.workbench;

import java.util.ArrayList;
import java.util.List;

/** Card members share one rectangle; resize occurs only on explicit layout/configuration changes. */
final class PaneLayout {
    static float divider(StageLayout.Bounds card,List<Float> weights,int index,boolean horizontal,float gap){
        float sum=0;for(int i=0;i<=index;i++)sum+=weights.get(i);
        return (horizontal?card.left:card.top)+((horizontal?card.width():card.height())-gap*(weights.size()-1))*sum+gap*(index+.5f);
    }
    static List<Float> moveDivider(StageLayout.Bounds card,List<Float> weights,int index,float coordinate,boolean horizontal,float gap,float minimum){
        if(index<0||index>=weights.size()-1)throw new IllegalArgumentException("Invalid divider");
        float available=(horizontal?card.width():card.height())-gap*(weights.size()-1);
        if(available<=0)return weights;
        float before=0;for(int i=0;i<index;i++)before+=weights.get(i);
        float pair=weights.get(index)+weights.get(index+1);
        float min=Math.min(Math.max(.000001f,minimum/available),Math.min(weights.get(index),weights.get(index+1)));
        float first=(coordinate-(horizontal?card.left:card.top)-gap*(index+.5f))/available-before;
        first=Math.max(min,Math.min(pair-min,first));
        List<Float> result=new ArrayList<>(weights);result.set(index,first);result.set(index+1,pair-first);return result;
    }
    static StageLayout.Bounds member(StageLayout.Bounds card,int index,List<Float> weights,int axis,float gap){
        if(weights.size()==1)return card;
        boolean horizontal=axis==1||(axis==0&&card.width()>=card.height());
        float available=(horizontal?card.width():card.height())-gap*(weights.size()-1),before=0;
        for(int i=0;i<index;i++)before+=weights.get(i);
        float start=available*before+gap*index,end=start+available*weights.get(index);
        return horizontal?new StageLayout.Bounds(card.left+start,card.top,card.left+end,card.bottom)
                :new StageLayout.Bounds(card.left,card.top+start,card.right,card.top+end);
    }
    static float weightAt(StageLayout.Bounds card, float x, float y, int count, boolean horizontal, float gap) {
        float available = (horizontal ? card.width() : card.height()) - gap * (count - 1);
        if (available <= 0) return .5f;
        float position = (horizontal ? x - card.left : y - card.top) - gap / 2;
        return Math.max(.2f, Math.min(.8f, position / available));
    }
    static StageLayout.Bounds member(StageLayout.Bounds card,int index,int count,float firstWeight,int axis,float gap){
        if(count<=1)return card;
        boolean horizontal=axis==1||(axis==0&&card.width()>=card.height());
        float length=horizontal?card.width():card.height(),available=length-gap*(count-1);
        float weight=index==0?firstWeight:(1-firstWeight)/(count-1);
        float before=index==0?0:available*(firstWeight+(index-1)*(1-firstWeight)/(count-1))+gap*index;
        return horizontal?new StageLayout.Bounds(card.left+before,card.top,card.left+before+available*weight,card.bottom)
                :new StageLayout.Bounds(card.left,card.top+before,card.right,card.top+before+available*weight);
    }
}
