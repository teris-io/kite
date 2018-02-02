/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.rabbitmq.client.ConnectionFactory;

import io.teris.kite.gson.JsonSerializer;
import io.teris.kite.rpc.amqp.AmqpServiceInvoker;
import io.teris.kite.rpc.amqp.AmqpServiceExporter;

//@Ignore
public class TestAmqpInvocationRoundtrip extends AbstractInvocationTestsuite {

	private static final String requestExchange = "RPC";

	private static AmqpServiceInvoker invoker;

	private static AmqpServiceExporter provider;

	@BeforeClass
	public static void init() throws Exception {
		preInit();
		port = 5672;

		ConnectionFactory connectionFactory = new ConnectionFactory();
		connectionFactory.setHost("127.0.0.1");
		connectionFactory.setPort(port);

		invoker = AmqpServiceInvoker.connectionFactory(connectionFactory)
			.requestExchange(requestExchange)
			.start();

		ServiceFactory factory = ServiceFactory.invoker(invoker)
			.serializer(JsonSerializer.builder().build())
			.build();

		syncService = factory.newInstance(SyncService.class);
		asyncService = factory.newInstance(AsyncService.class);
		throwingService = factory.newInstance(ThrowingService.class);

		provider = AmqpServiceExporter.connectionFactory(connectionFactory)
			.requestExchange(requestExchange)
			.export(exporter1)
			.export(exporter2)
			.start();
	}

	@AfterClass
	public static void teardown() throws Exception {
		invoker.close().get();
		provider.close().get();
	}
}
