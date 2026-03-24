/*
 * Copyright (c) 2021 Abex
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

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;
import net.runelite.cache.script.disassembler.Disassembler;

public class DumpVanilla
{
	public static void main(String ...args) throws IOException
	{
		File cacheRoot = new File(args[0]);
		File runeliteRoot = new File(args[1]);

		Store store = new Store(new FlatStorage(cacheRoot));
		store.load();

		Disassembler disassembler = new Disassembler();
		ScriptLoader loader = new ScriptLoader()
			.configureForRevision(store.getIndex(IndexType.CLIENTSCRIPT).getRevision());

		File scriptsDir = new File(runeliteRoot, "runelite-client/src/main/scripts");
		for(File hashFile : scriptsDir.listFiles())
		{
			if (!hashFile.getName().endsWith(".hash"))
			{
				continue;
			}

			File scriptFile = new File(hashFile.toString().replace(".hash", ".rs2asm"));
			byte[] rs2asmSrc = Files.toByteArray(scriptFile);
			ScriptSource oldModSource = new ScriptSource(new String(rs2asmSrc, StandardCharsets.UTF_8));

			int id = Integer.parseInt(oldModSource.getHeader().get(".id").getOperand());
			byte[] data = get(store, id);

			String nhash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(data).asBytes());
			Files.write(nhash.getBytes(StandardCharsets.UTF_8), hashFile);

			String srcStr = disassembler.disassemble(loader.load(id, data));
			Files.write(srcStr.getBytes(StandardCharsets.UTF_8), scriptFile);
		}
	}

	static byte[] get(Store store, int id) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		Archive archive = index.getArchive(id);
		if (archive == null)
		{
			return null;
		}
		byte[] compressed = storage.loadArchive(archive);
		return archive.decompress(compressed);
	}
}
