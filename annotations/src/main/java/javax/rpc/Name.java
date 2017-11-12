/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package javax.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Informs the service factory to export the annotated method or method argument under
 * the given name.
 *
 * The annotation is optional for methods (the method name will be used by default), but
 * required for all arguments of a method (because there is no gurantee that the class
 * is compiled with -parameters preserving parameter names, otherwise they get deleted).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface Name {

	/**
	 * The name under which to export the element.
	 */
	String value();
}