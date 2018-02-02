/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.fasterxml;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.teris.kite.Deserializer;
import io.teris.kite.Serializer;


public class JsonSerializer implements Serializer {

	private static final String CONTENT_TYPE = "application/json";

	private final Deserializer deserializer;

	private final ObjectMapper mapper;

	private final Charset charset;

	public JsonSerializer(ObjectMapper mapper) {
		this(mapper, StandardCharsets.UTF_8);
	}

	public JsonSerializer(ObjectMapper mapper, Charset charset) {
		this.mapper = mapper;
		this.charset = charset;
		deserializer = new JsonDeserializer(mapper, charset);
	}

	public static JsonSerializerBuilder builder() {
		return new JsonSerializerBuilder();
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return mapper.writeValueAsString(value).getBytes(charset);
			}
			catch (IOException ex) {
				throw new IllegalArgumentException(ex.getCause() != null ? ex.getCause() : ex);
			}
		});
	}

	@Nonnull
	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
