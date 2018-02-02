/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.fasterxml;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


public class JsonSerializerBuilder {

	private final ObjectMapper mapper;

	private Charset charset = StandardCharsets.UTF_8;

	JsonSerializerBuilder() {
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

		// mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
		// mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);

		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);
		mapper.registerModule(new JavaTimeModule());

		mapper.setSerializationInclusion(Include.NON_ABSENT);
	}

	public JsonSerializerBuilder withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}

	public ObjectMapper rawMapper() {
		return mapper;
	}

	public JsonSerializer build() {
		mapper.registerModule(new SimpleModule()
			.addDeserializer(Serializable.class, new SerializableDeserializer(charset)));
		return new JsonSerializer(mapper, charset);
	}

	private static class SerializableDeserializer extends StdScalarDeserializer<byte[]> {

		private final Charset charset;

		SerializableDeserializer(Charset charset) {
			super((Class<?>)null);
			this.charset = charset;
		}

		@Override
		public byte[] deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
			return jp.getCodec().readTree(jp).toString().getBytes(charset);
		}
	}
}
