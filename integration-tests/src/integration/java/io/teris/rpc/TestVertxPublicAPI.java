/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.teris.rpc.http.vertx.VertxServiceRouter;
import io.teris.rpc.serialization.json.GsonSerializer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;


public class TestVertxPublicAPI {

	@Service(replace = "io.teris.rpc.TestVertxPublicAPI")
	public interface ComputeService {
		HashMap<String, Integer> sum(Context context, @Name("data") HashMap<String, ArrayList<Integer>> data);
	}

	static class ComputeServiceImpl implements ComputeService {

		@Override
		public HashMap<String, Integer> sum(Context context, HashMap<String, ArrayList<Integer>> data) {
			HashMap<String, Integer> res = new HashMap<>();
			for (Entry<String, ArrayList<Integer>> entry: data.entrySet()) {
				res.put(entry.getKey(), Integer.valueOf(entry.getValue().stream().mapToInt(Integer::intValue).sum()));
			}
			return res;
		}
	}


	private static HttpServer server;

	private static HttpClient httpClient;


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

		httpClient = vertx.createHttpClient(new HttpClientOptions()
			.setDefaultHost("localhost")
			.setDefaultPort(port)
			.setMaxPoolSize(50));

		Router router = Router.router(vertx);

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(GsonSerializer.builder().build())
			.bind(ComputeService.class, new ComputeServiceImpl())
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
	public void post_to_compute_sum_success() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<byte[]> done = new CompletableFuture<>();
		HttpClientRequest httpRequest = httpClient.post("/compute/sum", httpResponse ->
			httpResponse.bodyHandler(buffer ->
				done.complete(buffer != null ? buffer.getBytes() : null)));
		httpRequest.setChunked(true).end("{\"data\":{\"set1\":[-1,5,10,-25],\"set2\":[13,-10,2,8]}}");
		String payload = new String(done.get(5, TimeUnit.SECONDS));
		assertTrue("{\"payload\":{\"set2\":13,\"set1\":-11}}".equals(payload)
			|| "{\"payload\":{\"set1\":-11,\"set2\":13}}".equals(payload));
	}

	@Test
	public void post_to_compute_sum_error() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<byte[]> done = new CompletableFuture<>();
		HttpClientRequest httpRequest = httpClient.post("/compute/sum", httpResponse ->
			httpResponse.bodyHandler(buffer ->
				done.complete(buffer != null ? buffer.getBytes() : null)));
		httpRequest.setChunked(true).end("{\"set1\":[-1,5,10,-25],\"set2\":[13,-10,2,8]}");
		String payload = new String(done.get(5, TimeUnit.SECONDS));
		assertTrue(payload.contains("\"errorMessage\":\"Too many arguments (5 instead of 2) to ComputeService.sum\""));
	}
}
