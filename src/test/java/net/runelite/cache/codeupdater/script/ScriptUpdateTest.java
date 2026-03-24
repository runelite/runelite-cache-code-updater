/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.codeupdater.script;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class ScriptUpdateTest
{
	@Test
	public void insertion()
	{
		testScript("insertion.txt");
	}

	@Test
	public void nearby1()
	{
		testScript("nearby1.txt");
	}

	@Test
	public void nearby2()
	{
		testScript("nearby2.txt");
	}

	@Test
	public void commandScript()
	{
		testScript("commandscript.rs2asm");
	}

	@Test
	public void privateMessage()
	{
		testScript("PrivateMessage.rs2asm");
	}

	@Test
	public void labelSwitch()
	{
		testScript("switch.txt");
	}

	@Test
	public void swap()
	{
		testScript("swap.txt");
	}

	@Test
	public void lvt()
	{
		testScript("lvt.txt", 1);
	}

	@Test
	public void lvtNoChange()
	{
		testScript("lvt_nochange.txt");
	}

	private void testScript(String name)
	{
		testScript(name, 0);
	}

	private void testScript(String name, int intLvtIncrement)
	{
		Map<Character, StringBuilder> fis = new HashMap<>();
		for (char c : new char[]{'s', 'm', 'n', 'f'})
		{
			fis.put(c, new StringBuilder());
		}
		new BufferedReader(new InputStreamReader(ScriptUpdateTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)).lines()
			.forEach(l -> {
				String line = l.substring(5);
				byte[] ident = l.substring(0, 4).getBytes(StandardCharsets.UTF_8);
				for (byte c : ident)
				{
					if (c != ' ')
					{
						fis.get((char)c).append(line).append('\n');
					}
				}
			});

		String out = ScriptUpdate.updateScript(
			new ScriptSource(fis.get('s').toString()),
			new ScriptSource(fis.get('n').toString()),
			new ScriptSource(fis.get('m').toString()),
			intLvtIncrement,
			0,
			0
		);

		Assert.assertEquals(fis.get('f').toString(), out);
	}
}
