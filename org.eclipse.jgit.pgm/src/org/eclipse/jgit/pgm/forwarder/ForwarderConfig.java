package org.eclipse.jgit.pgm.forwarder;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.pgm.Die;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class to parse the forwarder configuration.
 *
 * <pre>
 * [global]
 *     # Required. Any of the following forms are accepted:
 *     #   "0.0.0.0:9418"
 *     #   "localhost"   -> localhost:9418 (default port)
 *     listen = 127.0.0.1:9418
 *
 *     # Required. Same parsing rules as listen, but default port is 9419.
 *     remote = 127.0.0.1:9419
 *
 *     # Optional. If > 0, limits total concurrent forward connects.
 *     maxStart = 100
 *
 * # Optional per-project limits
 * # Only considers the first applicable match
 * # .git suffix not needed
 * 
 * [project "&lt;java-style-regex&gt;"]
 *     maxStart = 20
 * </pre>
 */
public class ForwarderConfig {
    /**
     * Listen or remote host and port pair.
     *
     * @param host host name or address
     * @param port port number
     */
    public record HostPort(String host, int port) {
        /**
         * Parse a "host:port" or "host" string.
         *
         * @param in string to parse (e.g. "127.0.0.1:9418" or "localhost")
         * @param defaultPort port used when none is specified in {@code in}
         * @return parsed HostPort
         */
        public static HostPort parse(String in, int defaultPort) {
            if (in == null) {
                throw new IllegalArgumentException("host/port must not be null");
            }

            String trimmed = in.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Empty host/port combination");
            }

            int colon = trimmed.lastIndexOf(':');
            if (colon > 0 && colon < trimmed.length() - 1) {
                String portPart = trimmed.substring(colon + 1);
                if (portPart.matches("\\d+")) {
                    return new HostPort(trimmed.substring(0, colon),
                            Integer.parseInt(portPart));
                }
            }
            return new HostPort(trimmed, defaultPort);
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    /**
     * Per-project concurrency limit identified by a regex pattern.
     *
     * @param pattern Java regex matched against the project path
     * @param maxStart maximum concurrent forward connections for matching projects
     */
    public record ProjectLimit(Pattern pattern, int maxStart) {
        /**
         * Validates pattern and maxStart.
         */
        public ProjectLimit {
            if (pattern == null) {
                throw new IllegalArgumentException("pattern must not be null");
            }
            if (maxStart <= 0) {
                throw new IllegalArgumentException("maxStart must be > 0");
            }
        }

        /**
         * Check whether the given project path matches this limit's pattern.
         *
         * @param project project path to match
         * @return true if the pattern matches
         */
        public boolean matches(String project) {
            return pattern.matcher(project).matches();
        }
    }

    private final HostPort listen;
    private final HostPort remote;
    private final List<ProjectLimit> projectLimits;
    private final int globalLimit;

    private static final String GLOBAL = "global";
    private static final String LISTEN = "listen";
    private static final String MAX_START = "maxStart";
    private static final String PROJECT = "project";
    private static final String REMOTE = "remote";

    /**
     * Build forwarder config from a JGit config.
     *
     * @param cfg config containing [global] listen, remote, maxStart and optional [project "regex"] maxStart
     * @throws Die if required keys are missing or invalid
     */
    public ForwarderConfig(Config cfg) throws Die {
        if (cfg == null) {
            throw new IllegalArgumentException("cfg must not be null");
        }

        String listenValue = cfg.getString(GLOBAL, null, LISTEN);
        String remoteValue = cfg.getString(GLOBAL, null, REMOTE);
        this.globalLimit = cfg.getInt(GLOBAL, null, MAX_START, -1);

        if (listenValue == null) {
            throw new Die("Missing global." + LISTEN);
        }
        if (remoteValue == null) {
            throw new Die("Missing global." + REMOTE);
        }

        this.listen = HostPort.parse(listenValue, 9419);
        this.remote = HostPort.parse(remoteValue, 9418);
        this.projectLimits = loadProjectLimits(cfg);
    }

    /**
     * Listen address (host and port) for the forwarder.
     *
     * @return listen HostPort from config
     */
    public HostPort getListen() {
        return listen;
    }

    /**
     * Remote (upstream) address to forward connections to.
     *
     * @return remote HostPort from config
     */
    public HostPort getRemote() {
        return remote;
    }

    /**
     * Per-project limits; first matching pattern applies.
     *
     * @return list of ProjectLimit entries from config
     */
    public List<ProjectLimit> getProjectLimits() {
        return projectLimits;
    }

    /**
     * Global maximum concurrent forward connections, or -1 if unset.
     *
     * @return global maxStart from config
     */
    public int getGlobalLimit() {
        return this.globalLimit;
    }

    private static List<ProjectLimit> loadProjectLimits(org.eclipse.jgit.lib.Config cfg) throws Die {
        Set<String> patterns = cfg.getSubsections(PROJECT);
        List<ProjectLimit> limits = new ArrayList<>();

        for (String pattern : patterns) {
            int max = cfg.getInt(PROJECT, pattern, MAX_START, -1);
            if (max <= 0) {
                continue;
            }
            try {
                limits.add(new ProjectLimit(Pattern.compile(pattern), max));
            } catch (IllegalArgumentException e) {
                throw new Die("Invalid project regex: " + pattern, e);
            }
        }
        return limits;
    }
}
