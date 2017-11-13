/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.testfixture;

import java.io.IOException;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;


class JsonSerializerBase {

	final ObjectMapper jsonMapper = new ObjectMapper();

	JsonSerializerBase() {
		jsonMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
		jsonMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
		jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		jsonMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);

		jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

		jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

		;
		jsonMapper.registerModule(new SimpleModule().addDeserializer(Serializable.class, new SerializableDeserializer()));
	}

	static class SerializableDeserializer extends StdDeserializer<Serializable> {

		public SerializableDeserializer() {
			this(null);
		}

		public SerializableDeserializer(Class<?> vc) {
			super(vc);
		}

		@Override
		public Serializable deserialize(JsonParser jp, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
			JsonNode node = jp.getCodec().readTree(jp);


			return node.toString().getBytes();
		}
	}
}
