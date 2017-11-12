/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/*
 * Copyright Profidata AG. All rights reserved.
 */
package io.teris.rpc;

import java.io.Serializable;
import javax.annotation.Nonnull;


/**
 * Provides support to serialize POJOs into byte array of `contentType`.
 */
public interface Serializer {

	/**
	 * Serializes the value into a byte array with the predefined content type.
	 *
	 * @throws IllegalArgumentException In case if an argument cannot be serialized.
	 */
	@Nonnull
	<CT extends Serializable> byte[] serialize(@Nonnull CT value);

	/**
	 * @return The content type expected and provided by the serializer.
	 */
	@Nonnull
	String contentType();

	/**
	 * Provides a matching deserializer.
	 */
	@Nonnull
	Deserializer deserializer();
}
