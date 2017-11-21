/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class ContextTest {

	@Test
	public void constructor_default_success() {
		assertEquals(0, new Context().size());
	}

	@Test
	public void constructor_copy_success() {
		Context context = new Context();
		context.put("key1", "value1");
		context.put("key2", "value2");
		assertEquals(context, new Context(context));
	}

	@Test
	public void mapInterfaceMethods_success() {
		Context context = new Context();
		context.put("key1", "value1");
		context.put("key2", "value2");
		assertFalse(context.isEmpty());

		assertTrue(context.keySet().contains("key1"));

		context.remove("key1");
		assertTrue(context.values().contains("value2"));
		assertTrue(context.containsKey("key2"));
		assertTrue(context.containsValue("value2"));
		assertEquals(1, context.size());

		context.clear();
		assertTrue(context.isEmpty());

	}
}
