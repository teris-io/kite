/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.fasterxml;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.teris.kite.Deserializer;


public class JsonDeserializer implements Deserializer {

	private final ObjectMapper mapper;

	public JsonDeserializer(ObjectMapper mapper) {
		this(mapper, StandardCharsets.UTF_8);
	}

	public JsonDeserializer(ObjectMapper mapper, Charset charset) {
		this.mapper = mapper;
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz) {

		return CompletableFuture.supplyAsync(() -> {
			try {
				return mapper.readValue(data, clazz);
			}
			catch (IOException ex) {
				throw new IllegalArgumentException(ex.getCause() != null ? ex.getCause() : ex);
			}
		});
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return mapper.readValue(data, mapper.constructType(type));
			}
			catch (IOException ex) {
				throw new IllegalArgumentException(ex.getCause() != null ? ex.getCause() : ex);
			}
		});
	}
}
