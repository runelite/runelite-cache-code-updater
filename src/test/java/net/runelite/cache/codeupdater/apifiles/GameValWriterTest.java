package net.runelite.cache.codeupdater.apifiles;

import junit.framework.TestCase;
import static net.runelite.cache.codeupdater.apifiles.GameValWriter.camelCase;
import org.junit.Test;

public class GameValWriterTest extends TestCase
{
	@Test
	public void testCamelCase()
	{
		assertEquals("FooBar", camelCase("foo_bar"));
		assertEquals("Foo23Bar", camelCase("foo_23_bar"));
		assertEquals("Foo23Bar", camelCase("foo_23bar"));
		assertEquals("Foo23Bar", camelCase("foo23_bar"));
		assertEquals("Foo23_64Bar", camelCase("foo_23_64_bar"));
		assertEquals("Foo23", camelCase("foo_23"));
		assertEquals("Foo1v1Info", camelCase("foo_1v1_info"));
		assertEquals("Foo64x64Info", camelCase("foo_64x64_info"));
		assertEquals("_1v1Info", camelCase("1v1_info"));
		assertEquals("_10v10Info", camelCase("10v10_info"));
	}
}