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
	 * Deserializes the byte array of data into an instance of `clazz`.
	 */
	@Nonnull
	<CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz);

	/**
	 * Deserializes the byte array of data into an instance of `type`.
	 */
	@Nonnull
	<CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Type type);
}
