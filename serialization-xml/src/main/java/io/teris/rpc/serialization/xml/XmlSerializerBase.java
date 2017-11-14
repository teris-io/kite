/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.xml;

import java.io.IOException;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator.Feature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


class XmlSerializerBase {

	final XmlMapper mapper = new XmlMapper();

	XmlSerializerBase() {
		mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
		mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
		mapper.configure(Feature.WRITE_XML_DECLARATION, true);

		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.registerModule(new JavaTimeModule());

		mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

		mapper.registerModule(new SimpleModule()
			.addDeserializer(Serializable.class, new SerializableDeserializer()));
	}

	static class SerializableDeserializer extends StdScalarDeserializer<byte[]> {

		SerializableDeserializer() {
			super((Class<?>) null);
		}

		@Override
		public byte[] deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
			return jp.getCodec().readTree(jp).toString().getBytes();
		}
	}
}
