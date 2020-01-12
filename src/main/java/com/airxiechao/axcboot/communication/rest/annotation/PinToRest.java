package com.airxiechao.axcboot.communication.rest.annotation;

import com.airxiechao.axcboot.communication.rest.aspect.AspectHandler;

import java.lang.annotation.*;

@Repeatable(PinToRests.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PinToRest {

    String desc();

    PinToRestWhen when();

    Class<? extends AspectHandler> handler();

}
