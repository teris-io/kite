/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Informs the service factory that the annotated class can be exported as an RPC service.
 * Allows to redefine the routing path. By default the routing path consists of the
 * canonical class name, in which the $-symbols are replaced with a dot and the ending
 * "Service" is erased from the service class (but not from any holding class in case of
 * static inner classes).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Service {

	/**
	 * The dot-separated path to be used as a full substitute value (if no value is provided
	 * for "replace") or as a replacement value for the value provided in "replace".
	 */
	String value() default "";

	/**
	 * The dot-separated path to replace in the default routing path. The "Service" ending
	 * is removed before applying the replacement, thus the replacement string should not
	 * contain it.
	 */
	String replace() default "";
}