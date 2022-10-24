package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Repository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefCollisionChecker {
	private Set<String> advRefsAndDirs;

	private Set<String> advRefs = new HashSet<>();

	private Set<String> noCollisionRefLvls = new HashSet<>();

	public RefCollisionChecker(Set<String> advRefs) {
		this.advRefs.addAll(advRefs);
	}

	public boolean existRefCollision(String refName) {
		// need at least one slash beyond refs
		if (!(refName.startsWith("refs") && refName.contains("/"))) {
			return true;
		}

		// is this refName an adv ref or contain an adv ref?
		if (getAdvRefsAndUpperDirs().contains(refName)) {
			return true;
		}

		// does an adv ref contain this refName?
		return isCollidedAtUpperDir(refName);
	}

	public boolean isCollidedAtUpperDir(String refName) {
		if (noCollisionRefLvls.contains(refName)) {
			return false;
		}

		if (advRefs.contains(refName)) {
			return true;
		}

		List<String> dirPrefixes = Arrays.asList(refName.split("/"));
		if (dirPrefixes.size() <= 2) {
			return false;
		}

		String curLvlName = dirPrefixes.get(dirPrefixes.size() - 1);
		int upperLvlLen = refName.length() - curLvlName.length() - 1;
		String upperLvlName = refName.substring(0, upperLvlLen);
		boolean upperCollided = isCollidedAtUpperDir(upperLvlName);
		if (!upperCollided) {
			noCollisionRefLvls.add(refName);
		}

		return upperCollided;
	}

	private Set<String> getAdvRefsAndUpperDirs() {
		if (advRefsAndDirs == null) {
			advRefsAndDirs = Repository.getRefsAndDirs(advRefs);
		}
		return advRefsAndDirs;
	}
}
