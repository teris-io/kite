/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.xml;

import java.io.Serializable;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;


public class XmlSerializer extends XmlSerializerBase implements Serializer {

	private static final String CONTENT_TYPE = "application/xml";

	private final Deserializer deserializer = new XmlDeserializer();

	public XmlSerializer() {
		super();
	}

	@Nonnull
	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public <V extends Serializable> byte[] serialize(@Nonnull V value) {
		try {
			return mapper.writeValueAsBytes(value);
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
