package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogWriter;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_NOTES;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

/**
 * Utility for reading reflog entries from file repository
 */
public abstract class FileReflogWriter implements org.eclipse.jgit.lib.ReflogWriter {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReflogWriter log(String refName, ReflogEntry entry) throws IOException {
        return log(refName, entry.getOldId(), entry.getNewId(), entry.getWho(),
                entry.getComment());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract ReflogWriter log(String refName, ObjectId oldId,
                                     ObjectId newId, PersonIdent ident, String message) throws IOException;

    static boolean shouldAutoCreateLog(Repository repo, String refName) {
        CoreConfig.LogRefUpdates value = repo.isBare()
                ? CoreConfig.LogRefUpdates.FALSE
                : CoreConfig.LogRefUpdates.TRUE;

        value = repo.getConfig().getEnum(ConfigConstants.CONFIG_CORE_SECTION,
                null, ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, value);
        if (value != null) {
            switch (value) {
                case FALSE:
                    break;
                case TRUE:
                    return refName.equals(HEAD) || refName.startsWith(R_HEADS)
                            || refName.startsWith(R_REMOTES)
                            || refName.startsWith(R_NOTES);
                case ALWAYS:
                    return refName.equals(HEAD) || refName.startsWith(R_REFS);
                default:
                    break;
            }
        }
        return false;
    }
}