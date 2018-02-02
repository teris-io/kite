/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import io.teris.kite.Context;
import io.teris.kite.Name;
import io.teris.kite.Serializer;
import io.teris.kite.Service;
import io.teris.kite.fasterxml.JsonSerializer;
import io.teris.kite.rpc.vertx.HttpServiceInvoker;
import io.teris.kite.rpc.vertx.HttpServiceExporter;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;


public class TestVertxInvocationLongBlocking {

	@Service
	public interface LongBlocking {
		Boolean doit(Context context, @Name("instance") Integer instance, @Name("seconds") Integer seconds) throws InterruptedException;
	}

	static class LongBlockingImpl implements LongBlocking {

		@Override
		public Boolean doit(Context context, Integer instance, Integer seconds) {
			long start = System.currentTimeMillis();
			for (int i = 0; i < seconds.intValue(); i++) {
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException ex) {
					// ignore
				}
				System.out.println(String.format("Elapsed %d: %f", instance, Double.valueOf((System.currentTimeMillis() - start) * 1e-3)));
			}
			return Boolean.TRUE;
		}
	}


	private static HttpServer server;

	private static LongBlocking service;


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

		Serializer serializer = JsonSerializer.builder().build();

		Vertx vertx = Vertx.vertx();

		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions()
			.setDefaultHost("localhost")
			.setDefaultPort(port)
			.setMaxPoolSize(50));

		HttpServiceInvoker invoker = HttpServiceInvoker.httpClient(httpClient).build();

		ServiceFactory factory = ServiceFactory.invoker(invoker)
			.serializer(serializer)
			.build();

		service = factory.newInstance(LongBlocking.class);

		ServiceExporter provider = ServiceExporter.serializer(serializer)
			.export(LongBlocking.class, new LongBlockingImpl())
			.build();

		Router router = HttpServiceExporter.router(vertx)
			.export(provider)
			.router();

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

	@Ignore
	@Test
	public void invoke_sync_blocking() throws InterruptedException, ExecutionException {
		List<Callable<Void>> callables = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			Integer j = Integer.valueOf(i);
			callables.add(() -> {
				service.doit(new Context(), j, Integer.valueOf(120));
				return null;
			});
		}
		ExecutorService executors = Executors.newCachedThreadPool();
		for (Future<Void> future: executors.invokeAll(callables)) {
			future.get();
		}
	}
}
