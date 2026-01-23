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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.codeupdater.mapper.Mapping;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.assembler.Assembler;
import net.runelite.cache.script.disassembler.Disassembler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class ScriptUpdate
{
	private static final String ILVT = "ilvt";
	private static final String SLVT = "Slvt";
	private static final Map<String, String> opcodeGroups = ImmutableMap.<String, String>builder()
		.put("iload", ILVT)
		.put("istore", ILVT)
		.put("ostore", SLVT)
		.put("oload", SLVT)
		.put("jump", "label")
		.put("if_icmpeq", "label")
		.put("if_icmpne", "label")
		.put("if_icmplt", "label")
		.put("if_icmpgt", "label")
		.put("if_icmple", "label")
		.put("if_icmpge", "label")
		.build();

	public static void update() throws IOException, GitAPIException
	{
		ScriptLoader loader = new ScriptLoader();

		String root = "runelite-client/src/main/scripts";
		Repository rl = Repo.RUNELITE.get();

		MutableCommit mc = new MutableCommit("Scripts");
		MutableCommit oldDelta = new MutableCommit("Old vanilla ", false);
		MutableCommit newDelta = new MutableCommit("New vanilla ", false);

		Main.execAllAndWait(GitUtil.listDirectory(rl, Main.branchName, root, n -> n.endsWith(".hash"))
			.entrySet()
			.stream()
			.map(ent -> () ->
			{
				String hashFile = ent.getKey();
				String scriptFile = hashFile.replace(".hash", ".rs2asm");
				String scriptFilePath = GitUtil.pathJoin(root, scriptFile);

				String rs2asmSrc = GitUtil.readFileString(rl, Main.branchName, scriptFilePath);
				ScriptSource oldModSource = new ScriptSource(rs2asmSrc);

				int id = Integer.parseInt(oldModSource.getHeader().get(".id").getOperand());

				String thash;
				try (ObjectReader or = rl.newObjectReader())
				{
					thash = new String(or.open(ent.getValue()).getBytes(), StandardCharsets.UTF_8).trim();
				}

				byte[] oData = get(Main.previous, id);
				if (oData == null)
				{
					mc.log("script {} does not exist", id);
					return;
				}

				String ohash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(oData).asBytes());

				if (!thash.equals(ohash))
				{
					mc.log("old cache hash does not match hash in tree. {} != {}", ohash, thash);
					return;
				}

				byte[] nData = get(Main.next, id);
				if (nData == null)
				{
					mc.log("lost script {} \"{}\"", id, scriptFile);

					newDelta.removeFile(scriptFilePath);
					newDelta.removeFile(GitUtil.pathJoin(root, hashFile));

					mc.writeFile(scriptFilePath, ("; lost script\n" + rs2asmSrc));

					return;
				}

				String nhash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(nData).asBytes());

				if (nhash.equals(ohash))
				{
					return;
				}

				Disassembler disassembler = new Disassembler();

				ScriptDefinition oldSrc = loader.load(id, oData);
				String oldSrcStr = disassembler.disassemble(oldSrc);
				ScriptSource oldSource = new ScriptSource(oldSrcStr);
				oldDelta.writeFile(scriptFilePath, oldSrcStr);

				ScriptDefinition newSrc = loader.load(id, nData);
				String newSrcStr = disassembler.disassemble(newSrc);
				ScriptSource newSource = new ScriptSource(newSrcStr);
				newDelta.writeFile(scriptFilePath, newSrcStr);

				String newModSource = updateScript(oldSource, newSource, oldModSource,
					newSrc.getLocalIntCount() - oldSrc.getLocalIntCount(),
					newSrc.getLocalObjCount() - oldSrc.getLocalObjCount());

				// Just make sure it atleast assembles still
				try
				{
					Assembler assembler = new Assembler(ScriptSource.RUNELITE_INSTRUCTIONS);
					assembler.assemble(new ByteArrayInputStream(newModSource.getBytes(StandardCharsets.UTF_8)));
				}
				catch (Exception e)
				{
					mc.log("Updated script does not assemble {}", scriptFile, e);
				}

				mc.writeFile(GitUtil.pathJoin(root, hashFile), nhash);
				mc.writeFile(scriptFilePath, newModSource);
				log.info("Updated script {}", scriptFile);
			}));

		try (Git git = new Git(rl))
		{
			String newBranch = Main.branchName + "-previous";
			git.branchCreate()
				.setForce(true)
				.setName(newBranch)
				.setStartPoint(Main.branchName)
				.call();
			oldDelta.finish(rl, newBranch);
			Repo.RUNELITE.pushBranch(newBranch);
		}
		mc.finish(rl, Main.branchName);

		try (Git git = new Git(rl))
		{
			String newBranch = Main.branchName + "-next";
			git.branchCreate()
				.setForce(true)
				.setName(newBranch)
				.setStartPoint(Main.branchName)
				.call();
			newDelta.finish(rl, newBranch);
			Repo.RUNELITE.pushBranch(newBranch);
		}
	}

	@VisibleForTesting
	static String updateScript(ScriptSource oldS, ScriptSource newS, ScriptSource oldM, int intLvtIncrement, int objLvtIncrement)
	{
		Mapping<ScriptSource.Line> osDom = Mapping.of(oldS.getLines(), oldM.getLines(), new ScriptSourceMapper());
		Mapping<ScriptSource.Line> osDns = Mapping.of(oldS.getLines(), newS.getLines(), new ScriptSourceMapper());

		Map<String, Integer> defaultLVTIncrement = Map.of(
			ILVT, intLvtIncrement,
			SLVT, objLvtIncrement);

		StringBuilder out = new StringBuilder();
		out.append(oldM.getPrelude());

		for (String key : oldM.getHeader().keySet())
		{
			ScriptSource.Line om = oldM.getHeader().get(key);
			ScriptSource.Line ns = newS.getHeader().get(key);

			String val = ns.getOperand();

			out.append(String.format("%-25s ", key));
			out.append(val);
			if (om.getComment() != null)
			{
				out.append(om.getComment());
			}
			out.append("\n");
		}

		Multimap<ScriptSource.Line, ScriptSource.Line> insertions = MultimapBuilder.hashKeys().arrayListValues().build();
		AtomicReference<ScriptSource.Line> lastOld = new AtomicReference<>(null);
		osDom.forEach((o, n) ->
		{
			if (o == null)
			{
				insertions.put(lastOld.get(), n);
			}
			else
			{
				lastOld.set(o);
			}
		});

		Map<String, String> operandMap = new HashMap<>();

		osDns.getSame().forEach((o, n) ->
		{
			if (!Objects.equals(o.getOpcode(), n.getOpcode()))
			{
				if (o.getOperand().isEmpty() && o.getOpcode().endsWith(":") && n.getOpcode().endsWith(":"))
				{
					operandMap.put("label\0" + o.getOpcode().substring(0, o.getOpcode().length() - 2),
						n.getOpcode().substring(0, n.getOpcode().length() - 2));
				}
				return;
			}

			String group = opcodeGroups.get(o.getOpcode());
			if (o.getOpcode().endsWith(":") && !o.getOperand().isEmpty() && !n.getOperand().isEmpty())
			{
				//case
				group = "label";
			}
			if (group != null)
			{
				operandMap.put(group + "\0" + o.getOperand(), n.getOperand());
			}
		});

		ScriptLineFormatConfig identityConfig = new ScriptLineFormatConfig();
		ScriptLineFormatConfig config = new ScriptLineFormatConfig()
		{
			@Override
			String mapLabel(String label)
			{
				String newLabel = operandMap.get("label\0" + label.substring(0, label.length() - 2));
				if (newLabel != null)
				{
					return newLabel + ":";
				}
				return label;
			}

			@Override
			public String mapOperand(String opcode, String operand)
			{
				String group = opcodeGroups.get(opcode);
				if (opcode.endsWith(":") && !operand.isEmpty())
				{
					// case
					group = "label";
				}
				if (group != null)
				{
					String override = operandMap.get(group + "\0" + operand);
					if (override != null)
					{
						return override;
					}
					Integer add = defaultLVTIncrement.get(group);
					if (add != null)
					{
						return "" + (Integer.parseInt(operand) + add);
					}
				}
				return operand;
			}
		};

		for (ScriptSource.Line l : insertions.get(null))
		{
			out.append(l.format(config)).append("\n");
		}
		insertions.removeAll(null);

		osDns.forEach((o, n) ->
		{
			if (n != null)
			{
				out.append(n.format(identityConfig));
				ScriptSource.Line oml = osDom.getSame().get(o);
				if (oml != null && oml.getComment() != null)
				{
					out.append(oml.getComment());
				}
				out.append("\n");
			}

			for (ScriptSource.Line l : insertions.get(o))
			{
				out.append(l.format(config)).append("\n");
			}
		});

		return out.toString();
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
