/*
 * Copyright (c) 2025 Abex
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

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import net.runelite.cache.DBRowManager;
import net.runelite.cache.DBTableManager;
import net.runelite.cache.IndexType;
import net.runelite.cache.InterfaceManager;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.definitions.GameValDefinition;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.GameValLoader;
import org.eclipse.jgit.api.errors.GitAPIException;

public class GameValWriter
{
	public static void update() throws IOException, GitAPIException
	{
		MutableCommit mc = new MutableCommit("GameVals");

		if (Main.next.getIndex(IndexType.GAMEVALS) == null)
		{
			mc = new MutableCommit("DO NOT MERGE GameVals missing");
			mc.log("broken cache");
			mc.finish(Repo.RUNELITE.get(), Main.branchName);
			return;
		}

		{
			var im = new ItemManager(Main.next);
			im.load();

			var normal = new IDClass("ItemID");
			var cert = normal.inner("Cert");
			var placeholder = normal.inner("Placeholder");

			for (var gv : loadGameVals(GameValLoader.ITEMS))
			{
				var oc = im.getItem(gv.getId());

				if (oc.notedTemplate != -1)
				{
					assert gv.getName().startsWith("CERT_");
					cert.add(gv.getName().substring(5), gv.getId());
				}
				else if (oc.placeholderTemplateId != -1)
				{
					assert gv.getName().startsWith("PLACEHOLDER_");
					placeholder.add(gv.getName().substring(12), gv.getId());
				}
				else
				{
					normal.add(gv.getName(), gv.getId(), oc.getName());
				}
			}

			normal.write(mc);
		}

		{
			var nm = new NpcManager(Main.next);
			nm.load();
			simple(mc, "NpcID", GameValLoader.NPCS, id -> nm.get(id).getName());
		}

		{
			var om = new ObjectManager(Main.next);
			om.load();
			simple(mc, "ObjectID", GameValLoader.OBJECTS, id -> om.getObject(id).getName());
		}

		{
			var root = new IDClass("DBTableID");
			var dtm = new DBTableManager(Main.next);
			dtm.load();
			var drm = new DBRowManager(Main.next);
			drm.load();
			var rowgvs = loadGameVals(GameValLoader.DBROWS).stream()
				.collect(Collectors.toMap(
					v -> v.getId(),
					v -> v));

			for (var tabgv : loadGameVals(GameValLoader.DBTABLES))
			{
				var dbtable = dtm.get(tabgv.getId());

				var rows = drm.getRows().stream()
					.filter(r -> r.getTableId() == tabgv.getId())
					.collect(Collectors.toList());

				if (rows.isEmpty())
				{
					continue;
				}

				var innerClass = root.inner(camelCase(tabgv.getName()));

				innerClass.add("ID", tabgv.getId());
				if (dbtable.getTypes() != null && dbtable.getTypes().length > 0)
				{
					for (var col : tabgv.getFiles().entrySet())
					{
						if (dbtable.getTypes().length > col.getKey())
						{
							var types = dbtable.getTypes()[col.getKey()];
							if (types == null)
							{
								continue;
							}

							String comment = types[0].getFullName();
							if (types.length > 1)
							{
								comment = "(" + Arrays.stream(types).map(t -> t.getFullName()).collect(Collectors.joining(", ")) + ")";
							}
							innerClass.add("COL_" + col.getValue(), col.getKey(), comment);
						}
					}
				}

				var rowClass = innerClass.inner("Row");
				for (var rid : rows)
				{
					var gv = rowgvs.get(rid.getId());
					var name = gv == null
						? "NULL_" + rid.getId()
						: gv.getName();
					rowClass.add(name, rid.getId());
				}
			}

			root.write(mc);
		}

		{
			var im = new InterfaceManager(Main.next);
			im.load();

			var root = new IDClass("InterfaceID");

			for (var gv : loadGameVals(GameValLoader.INTERFACES))
			{
				String className = camelCase(gv.getName());
				var inner = root.inner(className);

				root.add(gv.getName(), gv.getId());

				var iface = im.getIntefaceGroup(gv.getId());
				var names = new String[iface.length];
				var nameSet = new HashSet<String>();

				nameComponents(gv, iface, names, nameSet, -1);

				for (var ent : gv.getFiles().entrySet())
				{
					String name = names[ent.getKey()];

					inner.add(name, String.format("0x%04x_%04x", gv.getId(), ent.getKey()));
				}
			}

			root.write(mc);
		}

		simple(mc, "InventoryID", GameValLoader.INVENTORIES, id -> null);
		simple(mc, "VarPlayerID", GameValLoader.VARPS, id -> null);
		simple(mc, "VarbitID", GameValLoader.VARBITS, id -> null);
		simple(mc, "AnimationID", GameValLoader.ANIMATIONS, id -> null);
		simple(mc, "SpotanimID", GameValLoader.SPOTANIMS, id -> null);
		simple(mc, "VarClientID", GameValLoader.VARCS, id -> null);
		//simple(mc, "JingleID", GameValLoader.JINGLES, id -> null);

		new SpriteGameValUpdate(loadGameVals(GameValLoader.SPRITES)).update(mc);

		mc.finish(Repo.RUNELITE.get(), Main.branchName);
	}

	private static final Pattern GENERATED_NAME = Pattern.compile("com_[0-9]+");

	private static void nameComponents(GameValDefinition gv, InterfaceDefinition[] iface, String[] names, Set<String> nameSet, int parentId)
	{
		Multimap<String, Integer> namesThisLayer = HashMultimap.create();
		for (int i = 0; i < iface.length; i++)
		{
			if (iface[i].parentId != parentId)
			{
				continue;
			}

			String name = gv.getFiles().get(i);

			if (name != null && (GENERATED_NAME.matcher(name).matches() || name.isEmpty()))
			{
				name = null;
			}

			if (name != null)
			{
				name = IDClass.sanitizeSnake(name);
			}

			namesThisLayer.put(name, i);
		}

		namesThisLayer.asMap().forEach((baseName, components) ->
		{
			boolean generatedName = baseName == null;
			if (components.size() == 1 && !generatedName)
			{
				int cid = components.iterator().next();
				if (nameSet.add(baseName))
				{
					names[cid] = baseName;
					return;
				}
			}

			for (int cid : components)
			{
				String name = baseName;
				if (generatedName || components.size() > 1)
				{
					var n = Arrays.stream(iface)
						.filter(i -> i.getParentId() == parentId && (i.getId() & 0xFFFF) < cid)
						.count();

					String suffix = componentTypeName(iface[cid].getType()) + n;

					name = generatedName ? suffix : name + "_" + suffix;
				}

				if (!generatedName && nameSet.add(name))
				{
					names[cid] = name;
				}
				else
				{
					String parent = parentId == -1 ? "root" : names[parentId & 0xFFFF];
					name = parent + "_" + name;
					nameSet.add(name);
					names[cid] = name;
				}
			}
		});

		for (int i = 0; i < iface.length; i++)
		{
			if (iface[i].parentId != parentId || iface[i].type != 0)
			{
				continue;
			}

			nameComponents(gv, iface, names, nameSet, iface[i].id);
		}
	}

	private static String componentTypeName(int type)
	{
		switch (type)
		{
			case 0:
				return "layer";
			case 3:
				return "rect";
			case 4:
				return "text";
			case 5:
				return "graphic";
			case 6:
				return "model";
			case 9:
				return "line";
			default:
				return type + "_";
		}
	}

	static String camelCase(String str)
	{
		var sb = new StringBuilder();
		boolean wasUnderscore = true;
		boolean wasNumeric = false;
		for (int i = 0; i < str.length(); i++)
		{
			char c = str.charAt(i);

			boolean uppercase = wasUnderscore || wasNumeric;
			boolean keep = true;
			if (c == '_')
			{
				keep = false;
			}
			if (wasNumeric && i + 1 < str.length())
			{
				char next = str.charAt(i + 1);
				if (next >= '0' && next <= '9')
				{
					uppercase = false;
					keep = true;
				}
			}

			if (keep)
			{
				if (uppercase)
				{
					c = Character.toUpperCase(c);
				}

				sb.append(c);
			}

			wasUnderscore = c == '_' ;
			wasNumeric = c >= '0' && c <= '9';
		}

		return IDClass.sanitize(sb.toString());
	}

	private static void simple(MutableCommit mc, String name, int gvid, IntFunction<String> commenter)
	{
		var cl = new IDClass.Overflow(name);

		for (var gv : loadGameVals(gvid))
		{
			cl.add(gv.getName(), gv.getId(), commenter.apply(gv.getId()));
		}

		cl.write(mc);
	}

	@SneakyThrows
	private static List<GameValDefinition> loadGameVals(int id)
	{
		var i = Main.next.getIndex(IndexType.GAMEVALS);
		var a = i.getArchive(id);
		var cad = Main.next.getStorage().loadArchive(a);
		var fs = a.getFiles(cad);

		var load = new GameValLoader();

		return fs.getFiles().stream()
			.map(f -> load.load(id, f.getFileId(), f.getContents()))
			.collect(Collectors.toList());
	}
}
