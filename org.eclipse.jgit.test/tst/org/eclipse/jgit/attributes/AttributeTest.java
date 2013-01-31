package org.eclipse.jgit.attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.attributes.Attribute.State;
import org.junit.Test;


/**
 * Tests {@link Attribute}
 */
public class AttributeTest {

	@Test
	public void testBasic() {
		Attribute a = new Attribute("delta", State.SET);
		assertEquals(a.getKey(), "delta");
		assertEquals(a.getState(), State.SET);
		assertNull(a.getValue());
		assertEquals(a.toString(), "delta");

		a = new Attribute("delta", State.UNSET);
		assertEquals(a.getKey(), "delta");
		assertEquals(a.getState(), State.UNSET);
		assertNull(a.getValue());
		assertEquals(a.toString(), "-delta");

		a = new Attribute("delta", "value");
		assertEquals(a.getKey(), "delta");
		assertEquals(a.getState(), State.CUSTOM);
		assertEquals(a.getValue(), "value");
		assertEquals(a.toString(), "delta=value");
	}
}
