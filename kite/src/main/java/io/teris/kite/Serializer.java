/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.kite;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import io.teris.kite.Deserializer;


/**
 * Provides support to serialize POJOs into byte array of `contentType`.
 */
public interface Serializer {

	/**
	 * Asynchronously serializes the value into a byte array of the given content type.
	 * Completes exceptionally with an IllegalArgumentException when the argument cannot be
	 * serialized.
	 */
	@Nonnull
	<CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value);

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
