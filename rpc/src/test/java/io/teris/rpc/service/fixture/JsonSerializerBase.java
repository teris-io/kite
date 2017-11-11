/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service.fixture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


class JsonSerializerBase {

	final ObjectMapper jsonMapper = new ObjectMapper();

	JsonSerializerBase() {
		jsonMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
		jsonMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
		jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		jsonMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);

		jsonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

		jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
	}
}
