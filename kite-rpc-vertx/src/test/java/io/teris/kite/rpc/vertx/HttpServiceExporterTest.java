/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import org.junit.Test;

import io.teris.kite.Context;
import io.teris.kite.Service;
import io.teris.kite.rpc.ServiceExporter;
import io.teris.kite.gson.JsonSerializer;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.impl.RouteImpl;


public class HttpServiceExporterTest {

	@Service("upstream")
	public interface PingService {
		boolean ping(Context context);
	}

	static class PingServiceImpl implements PingService {

		@Override
		public boolean ping(Context context) {
			return true;
		}
	}

	@Test
	public void route_registersCaseInsensiticeEndpointsByDefault_success() throws Exception {
		ServiceExporter provider = ServiceExporter.serializer(JsonSerializer.builder().build())
			.export(PingService.class, new PingServiceImpl())
			.build();

		Router router = HttpServiceExporter.router(Vertx.vertx())
			.export(provider)
			.router();

		RouteImpl route = (RouteImpl) router.getRoutes().get(0);
		Field field = route.getClass().getDeclaredField("pattern");
		field.setAccessible(true);
		Pattern pattern = (Pattern) field.get(route);

		assertTrue(pattern.matcher("/Upstream/Ping").matches());
		assertTrue(pattern.matcher("/uPstreAm/ping").matches());
		assertFalse(pattern.matcher("/Upstream/PingSomething").matches());
		assertFalse(pattern.matcher("/Upstream/Ping/something").matches());
	}

	@Test
	public void route_registersCaseSensiticeEndpointsWhenRequested_success() throws Exception {
		ServiceExporter dispatcher = ServiceExporter.serializer(JsonSerializer.builder().build())
			.export(PingService.class, new PingServiceImpl())
			.build();

		Router router = Router.router(Vertx.vertx());

		HttpServiceExporter.router(router).caseSensitive()
			.export(dispatcher);

		RouteImpl route = (RouteImpl) router.getRoutes().get(0);
		Field field = route.getClass().getDeclaredField("pattern");
		field.setAccessible(true);
		Pattern pattern = (Pattern) field.get(route);

		assertNull(pattern);
		assertEquals("/upstream/ping", route.getPath());
	}
}
