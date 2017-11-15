/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.teris.rpc.Context;
import io.teris.rpc.Name;
import io.teris.rpc.Service;
import io.teris.rpc.ServiceCreator;
import io.teris.rpc.ServiceDispatcher;
import io.teris.rpc.serialization.json.JsonSerializer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;


public class VertxServiceInvokerTest {

	@Service
	public interface AService {

		Double add(Context context, @Name("a") Double a, @Name("b") Double b);

		CompletableFuture<Double> subtract(Context context, @Name("c") Double c, @Name("d") Double d);
	}

	private static class AServiceImpl implements AService {

		@Override
		public Double add(Context context, Double a, Double b) {
			context.put("remote-invocation", "add");
			return Double.valueOf(a.doubleValue() + b.doubleValue());
		}

		@Override
		public CompletableFuture<Double> subtract(Context context, Double c, Double d) {
			context.put("remote-invocation", "subtract");
			return CompletableFuture.supplyAsync(() -> Double.valueOf(c.doubleValue() - d.doubleValue()));
		}
	}

	// server side
	private static HttpServer server;

	// client side
	private static AService service;

	@BeforeClass
	public static void init() throws Exception {
		int port;
		while (true) {
			try (ServerSocket socket = new ServerSocket((int) (49152 + Math.random() * (65535 - 49152)))) {
				port = socket.getLocalPort();
				break;
			}
			catch (IOException e) {
				// repeat
			}
		}

		HttpServerOptions httpServerOptions = new HttpServerOptions().setHost("0.0.0.0").setPort(port);

		HttpClientOptions httpClientOptions = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port);

		Vertx vertx = Vertx.vertx();

		VertxServiceInvoker invoker = VertxServiceInvoker.builder(vertx)
			.httpClientOptions(httpClientOptions)
			.build();

		ServiceCreator factory = ServiceCreator.builder()
			.serviceInvoker(invoker)
			.serializer(new JsonSerializer())
			.build();

		service = factory.newInstance(AService.class);

		// http server listening on localhost:port and dispatching to the actual implementation

		Router router = Router.router(vertx);

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(new JsonSerializer())
			.bind(AService.class, new AServiceImpl())
			.build();

		VertxServiceRouter.builder(router).build()
			.route(dispatcher);

		CompletableFuture<HttpServer> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() ->
			vertx.createHttpServer(httpServerOptions)
				.requestHandler(router::accept)
				.listen(handler -> {
					if (handler.failed()) {
						promise.completeExceptionally(handler.cause());
						return;
					}
					promise.complete(handler.result());
				}));

		server = promise.get(5, TimeUnit.SECONDS);
	}

	@AfterClass
	public static void teardown() {
		server.close();
	}

	@Test
	public void invoke_sync_roundtrip_success() {
		Context context = new Context();
		Double res = service.add(context, Double.valueOf(341.2), Double.valueOf(359.3));
		assertEquals(700.5, res.doubleValue(), 0.001);
		assertEquals("add", context.get("remote-invocation"));
	}

	@Test
	public void invoke_async_roundtrip_success() throws Exception {
		Context context = new Context();
		CompletableFuture<Double> promise = service.subtract(context, Double.valueOf(359.3), Double.valueOf(341.2));
		assertEquals(18.1, promise.get().doubleValue(), 0.001);
		assertEquals("subtract", context.get("remote-invocation"));
	}
}
