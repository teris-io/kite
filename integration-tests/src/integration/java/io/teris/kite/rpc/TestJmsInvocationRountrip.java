/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.teris.kite.gson.JsonSerializer;
import io.teris.kite.rpc.jms.JmsServiceInvoker;
import io.teris.kite.rpc.jms.JmsServiceExporter;


public class TestJmsInvocationRountrip extends AbstractInvocationTestsuite {

	private static final String requestTopic = "RPC";

	private static BrokerService broker;

	private static JmsServiceInvoker invoker;

	private static JmsServiceExporter provider;

	@BeforeClass
	public static void init() throws Exception {
		preInit();

		String brokerUrl = String.format("tcp://localhost:%d", Integer.valueOf(port));
		String clientUrl = brokerUrl + "?jms.useAsyncSend=true";

		broker = new BrokerService();
		broker.setUseJmx(true);
		broker.addConnector(brokerUrl);
		broker.start();

		invoker = JmsServiceInvoker.connectionFactory(new ActiveMQConnectionFactory(clientUrl))
			.requestTopic(requestTopic)
			.start();

		ServiceFactory factory = ServiceFactory.invoker(invoker)
			.serializer(JsonSerializer.builder().build())
			.build();

		syncService = factory.newInstance(SyncService.class);
		asyncService = factory.newInstance(AsyncService.class);
		throwingService = factory.newInstance(ThrowingService.class);

		provider = JmsServiceExporter.connectionFactory(new ActiveMQConnectionFactory(clientUrl))
			.requestTopic(requestTopic)
			.export(exporter1)
			.export(exporter2)
			.start();
	}

	@AfterClass
	public static void teardown() throws Exception {
		invoker.close().get();
		provider.close().get();
		broker.stop();
	}
}
