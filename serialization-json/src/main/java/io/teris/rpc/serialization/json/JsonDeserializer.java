/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.json;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import javax.annotation.Nonnull;

import io.teris.rpc.Deserializer;


public class JsonDeserializer extends JsonSerializerBase implements Deserializer {

	public JsonDeserializer() {
		super();
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz) {
		try {
			return mapper.readValue(data, clazz);
		}
		catch (IOException ex) {
			throw new RuntimeException(ex.getCause() != null ? ex.getCause() : ex);
		}
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CT deserialize(@Nonnull byte[] data, @Nonnull Type type) {
		try {
			return mapper.readValue(data, mapper.constructType(type));
		}
		catch (IOException ex) {
			throw new RuntimeException(ex.getCause() != null ? ex.getCause() : ex);
		}
	}
}
