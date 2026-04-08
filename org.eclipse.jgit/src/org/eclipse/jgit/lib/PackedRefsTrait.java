package org.eclipse.jgit.lib;

/**
 * Headers defined on the packed-refs file.
 *
 * @since 7.7
 */
public enum PackedRefsTrait {
  /** If in the header, denotes the file has sorted data. */
  SORTED(" sorted"), //$NON-NLS-1$
  /** If in the header, denotes the file has peeled data for (refs/tags/...). */
  PEELED(" peeled"); //$NON-NLS-1$

  private final String value;

  PackedRefsTrait(String value) {
    this.value = value;
  }

  /**
   *  Gives the string representation of trait like it appears in packed-refs header
   *
   *  @return String representation of the trait.
   */
  public String value() {
    return value;
  }
}
