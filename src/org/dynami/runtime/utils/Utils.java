package org.dynami.runtime.utils;

public class Utils {

    public static boolean in(Object obj, Object... elements){
        for(Object e : elements){
            if(obj.equals(e)){
                return true;
            }
        }
        return false;
    }
}
