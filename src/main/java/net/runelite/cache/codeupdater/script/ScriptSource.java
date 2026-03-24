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

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.script.RuneLiteInstructions;
import net.runelite.cache.script.disassembler.Disassembler;

public class ScriptSource
{
	static final RuneLiteInstructions RUNELITE_INSTRUCTIONS = new RuneLiteInstructions();
	static
	{
		RUNELITE_INSTRUCTIONS.init();
	}

	@Getter
	private String prelude = "";

	@Getter
	private final Map<String, Line> header = new LinkedHashMap<>();

	@Getter
	private final ArrayList<Line> lines = new ArrayList<>();

	@Getter
	private final Map<String, Line> labels = new HashMap<>();

	@Getter
	@Setter
	@ToString
	public class Line
	{
		private String prefix;
		private String opcode;
		private String operand;
		private String comment;

		public void setOpcode(String newOpcode)
		{
			if (opcode != null && opcode.endsWith(":"))
			{
				labels.remove(opcode.substring(0, opcode.length() - 1));
			}
			opcode = newOpcode;
			if (opcode != null && opcode.endsWith(":"))
			{
				labels.put(opcode.substring(0, opcode.length() - 1), this);
			}
		}

		@Nullable
		public Integer getOpcodeInt()
		{
			if (opcode == null)
			{
				return null;
			}

			var opcode = RUNELITE_INSTRUCTIONS.findOpcodeFromName(this.opcode);
			if (opcode == null)
			{
				try
				{
					opcode = Integer.parseInt(this.opcode);
				}
				catch (NumberFormatException e)
				{
				}
			}
			return opcode;
		}

		public String format(ScriptLineFormatConfig config)
		{
			String s = getPrefix();
			if (s == null)
			{
				s = "";
			}

			String is = null;
			Integer in = getOpcodeInt();
			if (in != null)
			{
				if (config.isNoNames())
				{
					is = in + "";
				}
				else
				{
					is = RUNELITE_INSTRUCTIONS.findNameFromOpcode(in);
				}
			}
			if (is == null)
			{
				is = getOpcode();
			}
			if (is != null)
			{
				if (is.endsWith(":"))
				{
					s += config.mapLabel(is);
				}
				else
				{
					s += String.format("%-22s", is);
				}
			}

			if (getOperand() != null && !getOperand().isEmpty())
			{
				s += " " + config.mapOperand(getOpcode(), getOperand());
			}
			if (getComment() != null && !getComment().isEmpty())
			{
				s += getComment();
			}
			return s;
		}
	}

	private static Pattern LINE_MATCHER = Pattern.compile("(?m)^(?<prefix> *)(?<opcode>[^ ;\"\n]+ *)?(?<operand>[^;\n]*?)(?<comment> *;.*)?$");

	public ScriptSource()
	{
	}

	public ScriptSource(String source)
	{
		this();
		init(source);
	}

	public ScriptSource(ScriptDefinition d) throws IOException
	{
		this();
		Disassembler disassembler = new Disassembler();
		String s = disassembler.disassemble(d);
		init(s);
	}

	private void init(String source)
	{
		Matcher m = LINE_MATCHER.matcher(source);
		StringBuilder prelude = new StringBuilder();
		for (; m.find(); )
		{
			Line l = new Line();
			l.setPrefix(m.group("prefix"));
			String opcode = m.group("opcode");
			String trimOp = null;
			int extraWhitespace = 0;
			if (opcode != null)
			{
				trimOp = opcode.trim();
				extraWhitespace = opcode.length() - Math.max(trimOp.endsWith(":") ? 0 : 22, trimOp.length());
			}
			l.setOpcode(trimOp);
			l.setOperand(m.group("operand"));
			String comment = m.group("comment");
			if (l.getOperand().isEmpty() && extraWhitespace > 0 && comment != null)
			{
				comment = Strings.repeat(" ", extraWhitespace) + comment;
			}
			l.setComment(comment);
			if (l.getOpcode() != null && l.getOpcode().startsWith("."))
			{
				header.put(l.getOpcode(), l);
			}
			else if (!header.isEmpty())
			{
				lines.add(l);
			}
			else
			{
				prelude.append(m.group(0)).append("\n");
			}
		}
		this.prelude = prelude.toString();
	}
}
