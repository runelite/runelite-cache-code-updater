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
package net.runelite.cache.codeupdater.script;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.JavaIDList;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;

@Slf4j
public class ScriptIDUpdate
{
	public static void update() throws IOException
	{
		ScriptLoader loader = new ScriptLoader();

		JavaIDList.update("Script arguments", "runelite-api/src/main/java/net/runelite/api/ScriptID.java", (mc, l) ->
			{
				int id = l.intValue();

				if (id > 10000)
				{
					// RuneLite script
					return;
				}

				byte[] newScript = ScriptUpdate.get(Main.next, id);
				if (newScript == null)
				{
					mc.log("lost script {} {}", l.getName(), id);
					l.delete();
					return;
				}
				ScriptDefinition script = loader.load(id, newScript);

				var fdecl = l.getFdecl();
				AnnotationExpr allAnn = fdecl.getAnnotationByName("ScriptArguments").orElse(null);
				NormalAnnotationExpr ann = null;
				if (allAnn instanceof NormalAnnotationExpr)
				{
					ann = (NormalAnnotationExpr) allAnn;
				}
				if (allAnn instanceof MarkerAnnotationExpr)
				{
					fdecl.remove(allAnn);
				}
				if (ann == null)
				{
					ann = fdecl.addAndGetAnnotation("ScriptArguments");
				}
				ann.getPairs().clear();

				if (script.getIntArgCount() != 0)
				{
					ann.addPair("integer", new IntegerLiteralExpr(script.getIntArgCount()));
				}
				if (script.getLongArgCount() != 0)
				{
					ann.addPair("Long", new IntegerLiteralExpr(script.getLongArgCount()));
				}
				if (script.getObjArgCount() != 0)
				{
					ann.addPair("string", new IntegerLiteralExpr(script.getObjArgCount()));
				}
			});
	}
}
