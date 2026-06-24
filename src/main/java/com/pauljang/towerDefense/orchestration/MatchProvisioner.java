package com.pauljang.towerDefense.orchestration;

/**
 * The seam that replaces the monolith's in-process world copying (TDCommand.copyDirectoryNIO /
 * GameManager world creation). An implementation stands a match up somewhere and returns how to
 * reach it.
 *
 * <ul>
 *   <li>{@link DockerMatchProvisioner} runs one ephemeral container per match (the target model).</li>
 *   <li>A local/in-process implementation can wrap the legacy copy-world flow during migration, so
 *       callers depend only on this interface and the switch is a one-line wiring change.</li>
 * </ul>
 */
public interface MatchProvisioner {

    /** Stands up a server for {@code request} and returns its connection details. */
    MatchInstance provision(MatchRequest request) throws ProvisionException;

    /** Forcibly tears the instance down. The container also self-stops on {@code [TD-LIFECYCLE] ENDED}. */
    void shutdown(MatchInstance instance);

    /** Thrown when a match server could not be provisioned. */
    class ProvisionException extends Exception {
        public ProvisionException(String message) { super(message); }
        public ProvisionException(String message, Throwable cause) { super(message, cause); }
    }
}
