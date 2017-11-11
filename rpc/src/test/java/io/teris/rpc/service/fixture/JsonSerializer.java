/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service.fixture;

import java.io.Serializable;
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
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public <V extends Serializable> byte[] serialize(@Nonnull V value) {
		try {
			return jsonMapper.writeValueAsBytes(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
