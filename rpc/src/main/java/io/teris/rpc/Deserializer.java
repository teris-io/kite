/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/*
 * Copyright Profidata AG. All rights reserved.
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;


/**
 * Provides support to deserialize POJOs.
 */
public interface Deserializer {

	/**
	 * Asynchronously deserializes a byte array data into a new instance of the given class.
	 */
	@Nonnull
	<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz);

	/**
	 * Asynchronously deserializes a byte array data into a new instance of the given type.
	 */
	@Nonnull
	<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type);
}
