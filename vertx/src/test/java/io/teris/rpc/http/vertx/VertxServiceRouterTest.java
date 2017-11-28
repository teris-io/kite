/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import org.junit.Test;

import io.teris.rpc.Context;
import io.teris.rpc.Service;
import io.teris.rpc.ServiceDispatcher;
import io.teris.rpc.serialization.json.GsonSerializer;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.impl.RouteImpl;


public class VertxServiceRouterTest {

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
		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(GsonSerializer.builder().build())
			.bind(PingService.class, new PingServiceImpl())
			.build();

		Router router = Router.router(Vertx.vertx());

		VertxServiceRouter.builder(router).build()
			.route(dispatcher);

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
		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(GsonSerializer.builder().build())
			.bind(PingService.class, new PingServiceImpl())
			.build();

		Router router = Router.router(Vertx.vertx());

		VertxServiceRouter.builder(router).caseSensitive().build()
			.route(dispatcher);

		RouteImpl route = (RouteImpl) router.getRoutes().get(0);
		Field field = route.getClass().getDeclaredField("pattern");
		field.setAccessible(true);
		Pattern pattern = (Pattern) field.get(route);

		assertNull(pattern);
		assertEquals("/upstream/ping", route.getPath());
	}
}
