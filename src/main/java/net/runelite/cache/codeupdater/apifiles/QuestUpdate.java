/*
 * Copyright (c) 2023 Abex
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
import java.io.IOException;
import net.runelite.cache.DBRowManager;
import net.runelite.cache.DBTableIndexManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.util.Namer;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

public class QuestUpdate
{
	public static void update() throws IOException, GitAPIException
	{
		Repository rl = Repo.RUNELITE.get();

		String filePath = "runelite-api/src/main/java/net/runelite/api/Quest.java";
		String src = GitUtil.readFileString(rl, Main.branchName, filePath);
		int firstBrace = src.indexOf('{');

		StringBuilder dst = new StringBuilder(src.substring(0, firstBrace + 2));

		var idxMan = new DBTableIndexManager(Main.next);
		idxMan.load();
		var rowMan = new DBRowManager(Main.next);
		rowMan.load();
		var namer = new Namer();
		for (int row : idxMan.getMaster(0).getTupleIndexes().get(0).get(0))
		{
			var name = (String) rowMan.get(row).getColumnValues()[2][0];
			if (Strings.isNullOrEmpty(name))
			{
				continue;
			}

			var ident = namer.name(name, row);
			if (ident == null)
			{
				continue;
			}

			dst.append("\t").append(ident).append("(").append(row).append(", \"").append(name).append("\"),\n");
		}
		dst.append('\t');

		dst.append(src.substring(src.indexOf(';', firstBrace)));

		var mc = new MutableCommit("Quests");
		mc.writeFile(filePath, dst.toString());
		mc.finish(rl, Main.branchName);
	}
}
