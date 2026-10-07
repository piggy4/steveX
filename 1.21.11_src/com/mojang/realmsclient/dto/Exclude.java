package com.mojang.realmsclient.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Environment(EnvType.CLIENT)
public @interface Exclude {
}
