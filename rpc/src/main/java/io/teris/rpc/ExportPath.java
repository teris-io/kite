/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Tells the RPC exporter to copyInstance the class under a different routing path with package name
 * being the default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ExportPath {

	/**
	 * The dot-separated path to use instead of replace is replace given or instead of full default.
	 * If empty, then only used in part-replacement.
	 */
	String value();

	/**
	 * The dot-separated path to remove from the original (package.className{-Service}.toLowerCase())
	 */
	String replace() default "";
}