/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/*
 * Copyright Profidata AG. All rights reserved.
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Type;
import javax.annotation.Nonnull;


/**
 * Provides support to deserialize POJOs.
 */
public interface Deserializer {

	/**
	 * Deserializes byte array data into a new instance of `clazz`.
	 */
	@Nonnull
	<CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz);

	/**
	 * Deserializes byte array data into a new instance of `type`.
	 */
	@Nonnull
	<CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Type type);
}
