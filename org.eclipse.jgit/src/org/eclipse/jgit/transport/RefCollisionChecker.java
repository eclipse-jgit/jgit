package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Repository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The checker for checking against reference collision, a reference containing
 * another reference name wholly.
 */
public class RefCollisionChecker {
	private Set<String> curRefsAndDirs;

	private Set<String> curRefs = new HashSet<>();

	private Set<String> noCollisionRefLvls = new HashSet<>();

	/**
	 * Create a reference collision checker with a set of existing references to
	 * check against
	 * 
	 * @param curRefs a set of existing references
	 */
	public RefCollisionChecker(Set<String> curRefs) {
		this.curRefs.addAll(curRefs);
	}

	/**
	 * Checking whether the input refName collides with the existing references
	 * 
	 * @param refName a reference name
	 * @return true if collision exist, false otherwise
	 */
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

	/**
	 * Recursively checking the upper level directories of a reference name until a
	 * collision is found or reaching root directory
	 * 
	 * @param refLvl a reference name, or upper directory of a reference name
	 * @return true if collision exist, false otherwise
	 */
	public boolean isCollidedAtUpperDir(String refLvl) {
		if (noCollisionRefLvls.contains(refLvl)) {
			return false;
		}

		if (curRefs.contains(refLvl)) {
			return true;
		}

		List<String> dirPrefixes = Arrays.asList(refLvl.split("/"));
		if (dirPrefixes.size() <= 2) {
			return false;
		}

		String curLvlName = dirPrefixes.get(dirPrefixes.size() - 1);
		int upperLvlLen = refLvl.length() - curLvlName.length() - 1;
		String upperLvlName = refLvl.substring(0, upperLvlLen);
		boolean upperCollided = isCollidedAtUpperDir(upperLvlName);
		if (!upperCollided) {
			noCollisionRefLvls.add(refLvl);
		}

		return upperCollided;
	}

	private Set<String> getAdvRefsAndUpperDirs() {
		if (curRefsAndDirs == null) {
			curRefsAndDirs = Repository.getRefsAndDirs(curRefs);
		}
		return curRefsAndDirs;
	}
}
