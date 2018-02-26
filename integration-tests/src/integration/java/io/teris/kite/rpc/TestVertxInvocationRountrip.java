/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.teris.kite.Context;
import io.teris.kite.Service;
import io.teris.kite.gson.JsonSerializer;
import io.teris.kite.rpc.vertx.HttpServiceInvoker;
import io.teris.kite.rpc.vertx.HttpServiceExporter;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.handler.BodyHandler;


public class TestVertxInvocationRountrip extends AbstractInvocationTestsuite {

	private static HttpServer server;

	private static ServiceFactory creator;

	@BeforeClass
	public static void init() throws Exception {
		preInit();

		HttpServerOptions httpServerOptions = new HttpServerOptions().setHost("0.0.0.0").setPort(port);

		Vertx vertx = Vertx.vertx();

		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions()
			.setDefaultHost("localhost")
			.setDefaultPort(port)
			.setMaxPoolSize(200));

		HttpServiceInvoker invoker = HttpServiceInvoker.httpClient(httpClient).build();

		creator = ServiceFactory.invoker(invoker)
			.serializer(JsonSerializer.builder().build())
			.build();

		syncService = creator.newInstance(SyncService.class);
		asyncService = creator.newInstance(AsyncService.class);
		throwingService = creator.newInstance(ThrowingService.class);

		HttpServiceExporter exporter = HttpServiceExporter.router(vertx)
			.bodyHandler(BodyHandler.create().setBodyLimit(10000000))
			.preprocessor((ctx) -> {
				ctx.put("x-preprocessor", "acted");
				ctx.next();
			})
			.export(exporter1)
			.export(exporter2);

		CompletableFuture<HttpServer> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() ->
			vertx.createHttpServer(httpServerOptions)
				.requestHandler(exporter.router()::accept)
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

	@Service
	public interface NotServedService {
		void dosync(Context context);

		CompletableFuture<Void> doasync(Context context);
	}

	@Test
	public void roundtrip_notFoundException() {
		Context context = new Context();
		NotServedService service = creator.newInstance(NotServedService.class);
		try {
			service.dosync(context);
			throw new AssertionError("unreachable code");
		}
		catch (NotFoundException ex) {
			assertTrue(ex.getMessage().contains("Not Found"));
		}
		CompletableFuture<Void> promise = service.doasync(context);
		try {
			promise.get();
			throw new AssertionError("unreachable code");
		}
		catch (ExecutionException | InterruptedException ex) {
			assertTrue(ex.getMessage().contains("NotFoundException: Not Found"));
		}
	}

	@Test
	public void roundtrip_preprocessor_success() {
		Context context = new Context();
		Double res = syncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		assertEquals(700.5, res.doubleValue(), 0.001);
		assertEquals("acted", context.get("x-preprocessor"));
	}
}
