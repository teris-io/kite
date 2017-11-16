/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.testfixture;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;


public class JsonSerializer extends JsonSerializerBase implements Serializer {

	private static final String CONTENT_TYPE = "application/json";

	private final Deserializer deserializer = new JsonDeserializer();

	public JsonSerializer() {
		super();
	}

	@Nonnull
	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public <V extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull V value) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return mapper.writeValueAsBytes(value);
			}
			catch (JsonProcessingException ex) {
				throw new IllegalArgumentException(ex);
			}
		});
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
