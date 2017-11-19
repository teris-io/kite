/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.rabbitmq.client.ConnectionFactory;

import io.teris.rpc.amqp.AmqpServiceInvoker;
import io.teris.rpc.amqp.AmqpServiceRouter;
import io.teris.rpc.impl.AsyncServiceImpl;
import io.teris.rpc.impl.SyncServiceImpl;
import io.teris.rpc.impl.ThrowingServiceImpl;
import io.teris.rpc.serialization.json.GsonSerializer;

//@Ignore
public class TestAmqpInvocationRoundtrip extends AbstractInvocationTestsuite {

	private static final String exchangeName = "RPC";

	private static AmqpServiceInvoker invoker;

	private static AmqpServiceRouter router;

	private static final int port = 5672;

	@BeforeClass
	public static void init() throws Exception {

		ConnectionFactory connectionFactory = new ConnectionFactory();
		connectionFactory.setHost("127.0.0.1");
		connectionFactory.setPort(port);

		invoker = AmqpServiceInvoker.builder(connectionFactory)
			.exchangeName(exchangeName)
			.build()
			.start();

		ServiceCreator creator = ServiceCreator.builder()
			.serviceInvoker(invoker)
			.serializer(GsonSerializer.builder().build())
			.build();

		syncService = creator.newInstance(SyncService.class);
		asyncService = creator.newInstance(AsyncService.class);
		throwingService = creator.newInstance(ThrowingService.class);

		ServiceDispatcher dispatcher1 = ServiceDispatcher.builder()
			.serializer(GsonSerializer.builder().build())
			.bind(SyncService.class, new SyncServiceImpl("1"))
			.bind(AsyncService.class, new AsyncServiceImpl("2"))
			.build();

		ServiceDispatcher dispatcher2 = ServiceDispatcher.builder()
			.serializer(GsonSerializer.builder().build())
			.bind(ThrowingService.class, new ThrowingServiceImpl("3"))
			.build();

		router = AmqpServiceRouter.builder(connectionFactory)
			.exchangeName(exchangeName)
			.build()
			.route(dispatcher1)
			.route(dispatcher2)
			.start();
	}

	@AfterClass
	public static void teardown() {
		invoker.close();
		router.close();
	}
}
