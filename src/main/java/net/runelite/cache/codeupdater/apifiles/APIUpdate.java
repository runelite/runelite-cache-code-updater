/*
 * Copyright (c) 2019 Abex
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
package net.runelite.cache.codeupdater.apifiles;

import java.io.IOException;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.util.Namer;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

public class APIUpdate
{
	public static void update() throws IOException, GitAPIException
	{
		Repository repo = Repo.RUNELITE.get();

		MutableCommit mc = new MutableCommit("Legacy ID classes");

		{
			ItemManager im = new ItemManager(Main.next);
			im.load();
			var ids = new LegacyIDClass("ItemID", "ItemID");
			var nulls = new LegacyIDClass("NullItemID", "ItemID");
			for (var def : im.getItems())
			{
				if (def.name.equalsIgnoreCase("NULL"))
				{
					nulls.add(def.name, def.id);
				}
				else
				{
					ids.add(def.name, def.id);
				}
			}

			ids.write(mc);
			nulls.write(mc);
		}
		{
			ObjectManager om = new ObjectManager(Main.next);
			om.load();
			var ids = new LegacyIDClass("ObjectID", "ObjectID");
			var nulls = new LegacyIDClass("NullObjectID", "ObjectID");
			for (var def : om.getObjects())
			{
				if (def.getName().equalsIgnoreCase("NULL"))
				{
					nulls.add(def.getName(), def.getId());
				}
				else
				{
					ids.add(def.getName(), def.getId());
				}
			}

			ids.write(mc);
			nulls.write(mc);
		}

		{
			NpcManager nm = new NpcManager(Main.next);
			nm.load();
			var ids = new LegacyIDClass("NpcID", "NpcID");
			var nulls = new LegacyIDClass("NullNpcID", "NpcID");
			for (var def : nm.getNpcs())
			{
				if (def.name.equalsIgnoreCase("NULL"))
				{
					nulls.add(def.name, def.id);
				}
				else
				{
					ids.add(def.name, def.id);
				}
			}

			ids.write(mc);
			nulls.write(mc);
		}

		mc.finish(Repo.RUNELITE.get(), Main.branchName);
	}

	static class LegacyIDClass extends IDClass
	{
		private final Namer namer = new Namer();
		private int count = 0;

		public LegacyIDClass(String name, String replacement)
		{
			super(name, "net.runelite.api");
			setHeader("" +
				"/**\n" +
				" * @deprecated  Use {@link net.runelite.api.gameval." + replacement + "}\n" +
				" */\n" +
				"@Deprecated\n" +
				"@SuppressWarnings(\"unused\")\n");
		}

		public void add(String name, int id)
		{
			if (count > 32000)
			{
				return;
			}

			String javaName = namer.name(name, id);
			if (javaName == null)
			{
				return;
			}

			count++;

			super.add(javaName, id);
		}
	}
}
