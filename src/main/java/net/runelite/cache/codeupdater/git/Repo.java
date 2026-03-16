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
package net.runelite.cache.codeupdater.git;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.Settings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

@Slf4j
public enum Repo
{
	OSRS_CACHE,
	RUNELITE,
	SRN;

	private Repository repo;

	@Getter
	private boolean hasOrigin;

	static
	{
		SystemReader.setInstance(new SystemReader()
		{
			@Override
			public String getHostname()
			{
				return "rlccau";
			}

			@Override
			public String getenv(String variable)
			{
				return System.getenv(variable);
			}

			@Override
			public String getProperty(String key)
			{
				return System.getProperty(key);
			}

			@Override
			public FileBasedConfig openUserConfig(Config parent, FS fs)
			{
				var config = Settings.get("git.config");
				if (config.isEmpty())
				{
					return openSystemConfig(null, fs);
				}

				return new FileBasedConfig(parent, new File(config), fs);
			}

			@Override
			public FileBasedConfig openSystemConfig(Config parent, FS fs)
			{
				return new FileBasedConfig(null, fs)
				{
					@Override
					public void load()
					{
					}

					@Override
					public boolean isOutdated()
					{
						return false;
					}
				};
			}

			@Override
			public long getCurrentTime()
			{
				return System.currentTimeMillis();
			}

			@Override
			public int getTimezone(long when)
			{
				return getTimeZone().getOffset(when) / (60 * 1000);
			}
		});
	}

	public Repository get() throws IOException
	{
		if (repo != null)
		{
			return repo;
		}

		try
		{
			String name = name().toLowerCase();
			File localCache = new File(name);

			repo = new FileRepositoryBuilder()
				.setWorkTree(localCache)
				.build();

			try (Git git = new Git(repo))
			{
				if (git.remoteList().call().isEmpty())
				{
					repo.create();
				}

				hasOrigin = setRemote(git, name, "origin");

				if (setRemote(git, name, "upstream") && Settings.getBool("git.fetch.allowed"))
				{
					log.info("Updating {}", name);
					git.fetch()
						.setProgressMonitor(new TextProgressMonitor())
						.setRemote("upstream")
						.call();
					log.info("Done");
				}
			}

			return repo;
		}
		catch (GitAPIException e)
		{
			throw new AssertionError(e);
		}
	}

	private static boolean setRemote(Git git, String repoName, String remoteName)
	{
		String remoteURI = Settings.get("repo." + repoName + "." + remoteName);
		if (remoteURI.isEmpty())
		{
			return false;
		}
		try
		{
			git.remoteAdd()
				.setName(remoteName)
				.setUri(new URIish(remoteURI))
				.call();

			return true;
		}
		catch (GitAPIException | URISyntaxException e)
		{
			throw new RuntimeException(e);
		}
	}

	public void pushBranch(String branchName) throws GitAPIException
	{
		if (hasOrigin && Settings.getBool("git.push.allowed"))
		{
			try (Git git = new Git(repo))
			{
				log.info("Pushing {}", branchName);
				git.push()
					.setRemote("origin")
					.setRefSpecs(new RefSpec(branchName + ":" + branchName))
					.setProgressMonitor(new TextProgressMonitor())
					.setForce(true)
					.setThin(true)
					.call();
			}
		}
	}

	public static void closeAll()
	{
		for (Repo repo : values())
		{
			if (repo.repo != null)
			{
				repo.repo.close();
			}
		}
	}

	public void branch(String branchName) throws GitAPIException, IOException
	{
		get();

		try (Git git = new Git(get()))
		{
			git.branchCreate()
				.setForce(true)
				.setName(branchName)
				.setStartPoint(Settings.get("repo." + name().toLowerCase() + ".branch_point"))
				.call();
		}
	}
}
