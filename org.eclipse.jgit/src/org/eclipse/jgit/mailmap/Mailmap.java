package org.eclipse.jgit.mailmap;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Supports mapping to canonical identities according to a set of mailmap entries.
 */
public class Mailmap {
    private final List<MailmapEntry> entries;

    /**
     * Create an entry mailmap.
     */
    public Mailmap() {
        this.entries = new ArrayList<>();
    }

    /**
     * Produce a mailmap based upon the provided entries.
     * @param entries the canonical mappings defining the mailmap
     */
    public Mailmap(List<MailmapEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Map the given identity to a proper canonical entry, if defined in the mailmap. Otherwise,
     * return the provided identity.
     * @param ident the identity to map
     * @return a modified identity if matched against a mailmap entry, else the original entry
     */
    public PersonIdent map(@Nullable PersonIdent ident) {
        if (ident == null) {
            return null;
        }

        for (MailmapEntry entry : entries) {
            if (entry.matches(ident)) {
                return entry.map(ident);
            }
        }
        return ident;
    }

    /**
     * Append the provided mailmap entries to the end of the current mailmap. The current
     * mailmap's entries take precedence over those appended.
     *
     * @param mailmap the mailmap whose entries will be added to the current mailmap
     */
    public void append(Mailmap mailmap) {
        entries.addAll(mailmap.entries);
    }
}
