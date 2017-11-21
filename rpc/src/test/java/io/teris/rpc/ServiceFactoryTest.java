/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.testfixture.TestSerializer;


public class ServiceFactoryTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void builder_missingSerializer_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Serializer is required");
		ServiceFactory.builder()
			.serviceInvoker(mock(ServiceInvoker.class))
			.build();
	}

	@Test
	public void builder_missingServiceInvoker_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Service invoker is required");
		ServiceFactory.builder()
			.serializer(mock(Serializer.class))
			.build();
	}

	@Test
	public void builder_serializerAndServiceInvoker_success() {
		ServiceFactory.builder()
			.serializer(mock(Serializer.class))
			.serviceInvoker(mock(ServiceInvoker.class))
			.build();
	}

	@Test
	public void builder_allOptions_success() {
		Map<String, Deserializer> deserializerMap = new HashMap<>();
		ServiceFactory.builder()
			.serializer(mock(Serializer.class))
			.serviceInvoker(mock(ServiceInvoker.class))
			.deserializer("text", mock(Deserializer.class))
			.deserializers(deserializerMap)
			.uidGenerator(() -> "myUniqueId")
			.build();
	}

	@Service
	public interface SomeService {

		String method(Context context);
	}

	@Test
	public void newInstance_goodServiceDef_success() {
		ServiceFactory factory = ServiceFactory.builder()
			.serializer(new TestSerializer())
			.serviceInvoker(mock(ServiceInvoker.class))
			.build();

		factory.newInstance(SomeService.class);
	}

	@Test
	public void newInstance_invocation_successAndUidGenerator() {
		Context context = new Context();
		Entry<Context, byte[]> payload = new SimpleEntry<>(context, "{\"payload\":\"foo\"}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), isNull());
		ServiceFactory factory = ServiceFactory.builder()
			.serializer(new TestSerializer())
			.serviceInvoker(invoker)
			.uidGenerator(() -> "someId")
			.build();

		SomeService service = factory.newInstance(SomeService.class);
		String res = service.method(context);
		assertEquals("foo", res);
		assertEquals("someId", context.get(Context.X_REQUEST_ID_KEY));
	}
}
