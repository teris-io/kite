/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.teris.rpc.http.vertx.VertxServiceInvoker;
import io.teris.rpc.http.vertx.VertxServiceRouter;
import io.teris.rpc.impl.AsyncServiceImpl;
import io.teris.rpc.impl.SyncServiceImpl;
import io.teris.rpc.impl.ThrowingServiceImpl;
import io.teris.rpc.serialization.json.JsonSerializer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;


public class TestVertxInvocationRountrip extends AbstractInvocationTestsuite {

	private static HttpServer server;

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

		Vertx vertx = Vertx.vertx();

		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port));

		VertxServiceInvoker invoker = VertxServiceInvoker.builder(httpClient).build();

		ServiceCreator creator = ServiceCreator.builder()
			.serviceInvoker(invoker)
			.serializer(new JsonSerializer())
			.build();

		syncService = creator.newInstance(SyncService.class);
		asyncService = creator.newInstance(AsyncService.class);
		throwingService = creator.newInstance(ThrowingService.class);


		Router router = Router.router(vertx);

		ServiceDispatcher dispatcher1 = ServiceDispatcher.builder()
			.serializer(new JsonSerializer())
			.bind(SyncService.class, new SyncServiceImpl("1"))
			.bind(AsyncService.class, new AsyncServiceImpl("2"))
			.build();

		ServiceDispatcher dispatcher2 = ServiceDispatcher.builder()
			.serializer(new JsonSerializer())
			.bind(ThrowingService.class, new ThrowingServiceImpl("3"))
			.build();

		VertxServiceRouter.builder(router).build()
			.route(dispatcher1)
			.route(dispatcher2);

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
}
