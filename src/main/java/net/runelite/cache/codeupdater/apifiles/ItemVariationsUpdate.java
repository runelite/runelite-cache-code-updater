/*
 * Copyright (c) 2019 Tomas Slusny
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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.cache.ItemManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.util.Namer;

public class ItemVariationsUpdate
{
	private static String[][] replacements = new String[][]{
		{"null", ""},
		{"(clue|challenge) scroll \\(([^)]+)\\)", "clue scroll $2"},
		{"scroll box \\(([^)]+)\\)", "scroll box $1"},
		{"(sandstone|granite) \\(([^)]+)\\)", "$1$2"},
		{"\\([^)]+\\)", ""},
		{"[^a-zA-Z0-9 +]", ""},
		{" [0-9]+|[0-9]+ ", ""},
		{"uncharged | uncharged", ""},
		{"new | new", ""},
		{"half a ", ""},
		{"part ", ""},
		{"open(ed)? ", ""},
		{"  ", " "},
		{"\\w+ slayer helmet", "slayer helmet"},
		{"\\w+ abyssal whip", "abyssal whip"},
		{"magma helm|tanzanite helm", "serpentine helm"},
		{"trident of the seas", "trident"},
		{"trident of the swamp", "toxic trident"},
		{"toxic staff of the dead", "toxic staff"},
	};

	public static void update() throws IOException
	{
		ItemManager im = new ItemManager(Main.next);
		im.load();

		MutableCommit mc = new MutableCommit("Item variations");

		List<Function<String, String>> patterns = Stream.of(replacements)
			.map(a -> {
				Pattern p = Pattern.compile(a[0]);
				return (Function<String, String>) s -> p.matcher(s).replaceAll(a[1]);
			}).collect(Collectors.toList());

		Multimap<String, Integer> mmap = LinkedListMultimap.create();
		for (ItemDefinition def : im.getItems())
		{
			String name = Namer.removeTags(def.name).toLowerCase();

			for (Function<String, String> replace : patterns)
			{
				name = replace.apply(name);
			}

			name = name.trim();
			if (name.length() <= 0)
			{
				continue;
			}

			mmap.put(name, def.id);
		}

		Map<String, Collection<Integer>> map = mmap.asMap();
		map.entrySet().removeIf(e -> e.getValue().size() <= 1);
		mc.writeFile("runelite-client/src/main/resources/item_variations.json", Main.GSON.toJson(map));
		mc.finish(Repo.RUNELITE.get(), Main.branchName);
	}
}
