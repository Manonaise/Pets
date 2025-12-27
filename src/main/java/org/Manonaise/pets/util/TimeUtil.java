package org.Manonaise.pets.util;

public class TimeUtil {
    public static String format(long ms){
        if(ms <= 0) return "0m 0s";
        long s = ms/1000; long m = s/60; long r = s%60;
        return m + "m " + r + "s";
    }
}
