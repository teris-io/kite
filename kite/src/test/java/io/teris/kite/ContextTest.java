/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite;

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
		Context copied = new Context(context);
		assertEquals(context, copied);
		assertEquals(context.hashCode(), copied.hashCode());
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

	@Test
	public void keySet_preservesOriginalKeyCase_success() {
		Context context = new Context();
		context.put("KeY1", "value1");
		context.put("kEY2", "value2");
		assertEquals("[KeY1, kEY2]", context.keySet().toString());
	}

	@Test
	public void get_remove_containsKey_caseInsensitive_success() {
		Context context = new Context();
		context.put("KeY1", "value1");
		context.put("kEY2", "value2");
		assertEquals("value1", context.get("key1"));
		assertEquals("value1", context.get("KeY1"));
		assertEquals("value2", context.remove("key2"));
		assertEquals(1, context.size());
		assertTrue(context.containsKey("key1"));
		assertFalse(context.containsKey("key2"));
	}

	@Test
	public void put_sameValueInDifferenceCase_overwrites_success() {

	}


}
