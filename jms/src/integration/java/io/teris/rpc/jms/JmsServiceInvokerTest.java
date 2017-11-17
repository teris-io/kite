/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.jms;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.BusinessException;
import io.teris.rpc.Context;
import io.teris.rpc.Name;
import io.teris.rpc.Service;
import io.teris.rpc.ServiceCreator;
import io.teris.rpc.ServiceDispatcher;
import io.teris.rpc.serialization.json.JsonSerializer;


public class JmsServiceInvokerTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Service
	public interface AService {

		Double add(Context context, @Name("a") Double a, @Name("b") Double b);

		CompletableFuture<Double> subtract(Context context, @Name("c") Double c, @Name("d") Double d);

		Double exceptionally(Context context);

		CompletableFuture<Double> completingExceptionally(Context context);
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

		@Override
		public Double exceptionally(Context context) {
			context.put("remote-invocation", "exceptionally");
			return Double.valueOf("abc");
		}

		@Override
		public CompletableFuture<Double> completingExceptionally(Context context) {
			context.put("remote-invocation", "completingExceptionally");
			return CompletableFuture.supplyAsync(() -> Double.valueOf("abc"));
		}
	}

	private static final String topicName = "RPC";

	private static AService service;

	private static BrokerService broker;

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

		String brokerUrl = String.format("tcp://localhost:%d", Integer.valueOf(port));
		String clientUrl = brokerUrl + "?jms.useAsyncSend=true";

		broker = new BrokerService();
		broker.setUseJmx(true);
		broker.addConnector(brokerUrl);
		broker.start();

		JmsServiceInvoker invoker = JmsServiceInvoker.builder(new ActiveMQConnectionFactory(clientUrl))
			.topicName(topicName)
			.build()
			.start();

		ServiceCreator creator = ServiceCreator.builder()
			.serviceInvoker(invoker)
			.serializer(new JsonSerializer())
			.build();

		service = creator.newInstance(AService.class);


		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(new JsonSerializer())
			.bind(AService.class, new AServiceImpl())
			.build();

		JmsServiceRouter.builder(new ActiveMQConnectionFactory(clientUrl))
			.topicName(topicName)
			.build()
			.route(dispatcher)
			.start();
	}

	@AfterClass
	public static void teardown() throws Exception {
		broker.stop();
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
		double actual = promise.get().doubleValue();
		assertEquals(18.1, actual, 0.001);
		assertEquals("subtract", context.get("remote-invocation"));
	}

	@Test
	public void invoke_sync_exception_success() {
		Context context = new Context();
		exception.expect(BusinessException.class);
		exception.expectMessage("NumberFormatException: For input string: \"abc\"");
		service.exceptionally(context);
	}

	@Test
	public void invoke_sync_exception_success_contextUpdate() {
		Context context = new Context();
		try {
			service.exceptionally(context);
			throw new AssertionError("unreachable code");
		}
		catch (RuntimeException ex) {
			assertEquals("exceptionally", context.get("remote-invocation"));
		}
	}

	@Test
	public void invoke_async_exception_success() throws Exception {
		Context context = new Context();
		CompletableFuture<Double> promise = service.completingExceptionally(context);
		exception.expect(ExecutionException.class);
		exception.expectMessage("io.teris.rpc.BusinessException: NumberFormatException: For input string: \"abc\"");
		promise.get();
	}

	@Ignore
	@Test
	public void invoke_benchmark_async_success() throws Exception{
		Context context = new Context();
		List<CompletableFuture<Double>> promises = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			promises.add(service.subtract(context, Double.valueOf(341.2), Double.valueOf(359.3)));
		}
		CompletableFuture.allOf(promises.toArray(new CompletableFuture[]{})).get();
	}

	@Ignore
	@Test
	public void invoke_benchmark_sync_success() {
		Context context = new Context();
		for (int i = 0; i < 20000; i++) {
			service.add(context, Double.valueOf(341.2), Double.valueOf(359.3));
		}
	}

}
