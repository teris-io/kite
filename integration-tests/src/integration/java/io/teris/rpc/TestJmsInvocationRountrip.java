/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.teris.rpc.impl.AsyncServiceImpl;
import io.teris.rpc.impl.SyncServiceImpl;
import io.teris.rpc.impl.ThrowingServiceImpl;
import io.teris.rpc.jms.JmsServiceInvoker;
import io.teris.rpc.jms.JmsServiceRouter;
import io.teris.rpc.serialization.json.GsonSerializer;


public class TestJmsInvocationRountrip extends AbstractInvocationTestsuite {

	private static final String topicName = "RPC";

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

		ServiceFactory creator = ServiceFactory.builder()
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

		JmsServiceRouter.builder(new ActiveMQConnectionFactory(clientUrl))
			.topicName(topicName)
			.build()
			.route(dispatcher1)
			.route(dispatcher2)
			.start();
	}

	@AfterClass
	public static void teardown() throws Exception {
		broker.stop();
	}
}
