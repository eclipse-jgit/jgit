package org.eclipse.jgit.lib;

/**
 * Decorate a Ref with the getUpdateIndex implementation.
 */
public class VersionedRef implements Ref {

	private Ref instance;

	private long updateIndex;

	/**
	 * @param instance
	 *            the Reference
	 * @param updateIndex
	 *            its update index
	 */
	public VersionedRef(Ref instance, long updateIndex) {
		this.instance = instance;
		this.updateIndex = updateIndex;
	}

	/*
	 * public static VersionedRef from(Ref instance, long updateIndex) { return
	 * new VersionedRef(instance, updateIndex); }
	 */
	@Override
	public String getName() {
		return instance.getName();
	}

	@Override
	public boolean isSymbolic() {
		return instance.isSymbolic();
	}

	@Override
	public Ref getLeaf() {
		return instance.getLeaf();
	}

	@Override
	public Ref getTarget() {
		return instance.getTarget();
	}

	@Override
	public ObjectId getObjectId() {
		return instance.getObjectId();
	}

	@Override
	public ObjectId getPeeledObjectId() {
		return instance.getPeeledObjectId();
	}

	@Override
	public boolean isPeeled() {
		return instance.isPeeled();
	}

	@Override
	public Storage getStorage() {
		return instance.getStorage();
	}

	@Override
	public long getUpdateIndex() {
		return updateIndex;
	}

}
