package net.runelite.cache.codeupdater.apifiles;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.runelite.cache.codeupdater.JavaFile;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.definitions.GameValDefinition;

@RequiredArgsConstructor
public class SpriteGameValUpdate
{
	private final List<GameValDefinition> sprites;

	private final Map<Integer, FieldSNode> fieldByID = new HashMap<>();
	private final Map<String, ClassSNode> classByName = new HashMap<>();

	public void update(MutableCommit mc) throws IOException
	{
		var path = "runelite-api/src/main/java/net/runelite/api/gameval/SpriteID.java";

		String src = GitUtil.readFileString(Repo.RUNELITE.get(), Main.branchName, path);
		var fi = new JavaFile(Repo.RUNELITE.get(), Main.branchName, path);

		var rootSNode = new ClassSNode();
		{
			var sc = new StringChunker(src);
			buildSNode(sc, fi.getCompilationUnit(), rootSNode, true);
			sc.takeUntilLine(Integer.MAX_VALUE, rootSNode);
		}

		for (var gv : sprites)
		{
			var split = gv.getName().split(",");

			var field = fieldByID.get(gv.getId());

			var owner = rootSNode;
			if (split.length > 1)
			{
				var className = GameValWriter.camelCase(split[0]);
				owner = classByName.get(className);

				if (owner == null)
				{
					owner = new ClassSNode();
					rootSNode.insert(owner, ClassSNode.class, null);
					classByName.put(className, owner);
					owner.add(new OtherSNode("\n\tpublic static final class " + className + "\n\t{\n"));
					owner.add(new OtherSNode("\t}\n"));
				}
			}

			if (field != null)
			{
				fieldByID.remove(gv.getId());
			}

			if (field == null)
			{
				field = new FieldSNode("", gv.getId());
			}

			field.name = IDClass.sanitizeSnake(split[split.length - 1]);

			if (field.parent != owner)
			{
				if (field.parent != null)
				{
					field.remove();
				}

				owner.insert(field, FieldSNode.class, f -> f.value);
			}
		}

		for (var v : fieldByID.values())
		{
			v.remove();
		}

		var sb = new StringBuilder();
		rootSNode.print(sb, "");
		mc.writeFile(path, sb.toString());
	}

	@RequiredArgsConstructor
	static class StringChunker
	{
		private final String str;

		int offset = 0;
		int line = 1;

		void takeUntilLine(int line, ClassSNode into)
		{
			String start = this.str.substring(offset, advance(line));
			if (start.length() > 0)
			{
				var other = new OtherSNode(start);
				into.add(other);
			}
		}

		void takeUntilNode(Node node, ClassSNode into)
		{
			var range = node.getRange().get();
			takeUntilLine(range.begin.line, into);
		}

		void skipThroughNode(Node node)
		{
			var range = node.getRange().get();
			advance(range.end.line + 1);
		}

		int advance(int line)
		{
			while (this.line < line && this.offset < str.length())
			{
				if (str.charAt(this.offset++) == '\n')
				{
					this.line++;
				}
			}

			return this.offset;
		}
	}

	// JavaParser's LexicalPreservingParser doesn't let us generate
	// this much new code easily, so we use JP to chunk text into a
	// very loose tree
	static abstract class SNode
	{
		ClassSNode parent;

		void remove()
		{
			this.parent.body.remove(this);
			this.parent = null;
		}

		abstract void print(StringBuilder sb, String indent);
	}

	@AllArgsConstructor
	static class OtherSNode extends SNode
	{
		String content;

		@Override
		void print(StringBuilder sb, String indent)
		{
			sb.append(content);
		}
	}

	@AllArgsConstructor
	static class FieldSNode extends SNode
	{
		String name;
		int value;

		@Override
		void print(StringBuilder sb, String indent)
		{
			sb.append(indent).append("public static final int ").append(name).append(" = ").append(value).append(";\n");
		}
	}

	static class ClassSNode extends SNode
	{
		List<SNode> body = new ArrayList<>();

		void add(SNode node)
		{
			this.body.add(node);
			node.parent = this;
		}

		<T extends SNode> void insert(T node, Class<T> clazz, ToIntFunction<T> cmp)
		{
			int index = -1;
			for (var i = 0; i < body.size(); i++)
			{
				if (!clazz.isInstance(body.get(i)))
				{
					continue;
				}

				if (cmp != null && cmp.applyAsInt((T) body.get(i)) > cmp.applyAsInt(node))
				{
					break;
				}

				index = i;
			}

			if (index == -1)
			{
				index = cmp == null ? body.size() - 1 : 1;
			}
			else
			{
				index += 1;
			}

			this.body.add(index, node);
			node.parent = this;
		}

		void print(StringBuilder sb, String indent)
		{
			indent += "\t";
			for (var n : body)
			{
				n.print(sb, indent);
			}
		}
	}

	void buildSNode(StringChunker sc, Node node, ClassSNode into, boolean unwrap)
	{
		if (node instanceof TypeDeclaration<?> && !unwrap)
		{
			var td = (TypeDeclaration<?>) node;
			sc.takeUntilNode(td, into);
			var cn = new ClassSNode();
			into.add(cn);
			classByName.put(td.getName().asString(), cn);
			for (var inner : node.getChildNodes())
			{
				buildSNode(sc, inner, cn, false);
			}
			sc.takeUntilLine(td.getRange().get().end.line + 1, cn);
		}
		else if (node instanceof FieldDeclaration)
		{
			var fd = (FieldDeclaration) node;
			var init = fd.getVariable(0).getInitializer().orElse(null);
			if (init instanceof IntegerLiteralExpr)
			{
				var fsn = new FieldSNode(
					fd.getVariable(0).getName().asString(),
					((IntegerLiteralExpr) init).asInt()
				);
				SNode prev = fieldByID.put(fsn.value, fsn);
				if (prev != null)
				{
					prev.remove();
				}
				sc.takeUntilNode(node, into);
				sc.skipThroughNode(node);
				into.add(fsn);
			}
		}
		else
		{
			for (var inner : node.getChildNodes())
			{
				buildSNode(sc, inner, into, unwrap && !(node instanceof TypeDeclaration<?>));
			}
		}
	}
}
