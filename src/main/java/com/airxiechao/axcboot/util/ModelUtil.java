package com.airxiechao.axcboot.util;

import net.sf.cglib.beans.BeanCopier;

public class ModelUtil {

    public static void copyProperties(Object orig, Object dest){
        BeanCopier copier = BeanCopier.create(orig.getClass(), dest.getClass(), false);
        copier.copy(orig, dest, null);
    }

}
