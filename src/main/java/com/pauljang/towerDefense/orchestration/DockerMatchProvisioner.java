package com.pauljang.towerDefense.orchestration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Provisions a match by running one ephemeral container of the {@code docker/match-server} image,
 * waiting until its PaperMC server actually accepts connections, then handing back the published
 * host port. Talks to the local Docker daemon through the {@code docker} CLI (via
 * {@link ProcessBuilder}) so the plugin pulls in no Docker-Java/HTTP dependency.
 *
 * <p>Map templates are bind-mounted read-only from {@code mapsDir} on the host, so they can be
 * edited and dropped in without rebuilding the image. A Velocity forwarding secret, if supplied, is
 * passed as an env var the container's entrypoint turns into modern-forwarding config.
 */
public final class DockerMatchProvisioner implements MatchProvisioner {

    /** docker run/port/rm are quick; the long wait is the readiness poll, bounded separately. */
    private static final int CLI_TIMEOUT_SECONDS = 30;
    /** Where the container's plugin reads map templates from (server world container). */
    private static final String TEMPLATES_PATH = "/opt/td-server/GAME_WORLD_TEMPLATES";

    private final String image;
    private final String mapsDir;          // host dir bind-mounted read-only as the map templates
    private final String velocitySecret;   // null/blank => no proxy forwarding
    private final long readinessTimeoutSeconds;
    private final Logger log;

    public DockerMatchProvisioner(String image, String mapsDir, String velocitySecret,
                                  long readinessTimeoutSeconds, Logger log) {
        this.image = image;
        this.mapsDir = mapsDir;
        this.velocitySecret = velocitySecret;
        this.readinessTimeoutSeconds = readinessTimeoutSeconds;
        this.log = log;
    }

    @Override
    public MatchInstance provision(MatchRequest request) throws ProvisionException {
        String containerName = "td-match-" + dockerSafe(request.matchId());
        String containerId = runDocker(buildRunCommand(request, containerName), "start container").trim();
        if (containerId.isEmpty()) {
            throw new ProvisionException("docker run returned no container id for " + containerName);
        }
        // Everything after the container starts must reap it on failure so we never orphan a container.
        try {
            String mapping = runDocker(List.of("docker", "port", containerId, "25565/tcp"), "read published port");
            HostPort hp = parseHostPort(mapping);
            awaitReachable(hp.host(), hp.port());
            log.info("[orchestration] match " + request.matchId() + " -> container " + containerId
                    + " reachable on " + hp.host() + ":" + hp.port());
            return new MatchInstance(request.matchId(), containerId, hp.host(), hp.port());
        } catch (ProvisionException e) {
            safeRemove(containerId);
            throw e;
        }
    }

    private List<String> buildRunCommand(MatchRequest request, String containerName) {
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "-d",
                "--name", containerName,
                "--label", "td.matchId=" + request.matchId(),
                "-P"));
        // Maps are bind-mounted read-only so they can be edited on the host without rebuilding.
        if (mapsDir != null && !mapsDir.isBlank()) {
            cmd.add("-v");
            cmd.add(new File(mapsDir).getAbsolutePath() + ":" + TEMPLATES_PATH + ":ro");
        }
        cmd.add("-e"); cmd.add("TD_MATCH_ID=" + request.matchId());
        cmd.add("-e"); cmd.add("TD_MAP_ID=" + request.mapId());
        if (velocitySecret != null && !velocitySecret.isBlank()) {
            cmd.add("-e"); cmd.add("TD_VELOCITY_SECRET=" + velocitySecret);
        }
        cmd.add(image);
        return cmd;
    }

    @Override
    public void shutdown(MatchInstance instance) {
        if (instance.containerId() == null || instance.containerId().isBlank()) return;
        safeRemove(instance.containerId());
    }

    /** Polls the match server's port until it accepts a connection, so the proxy never routes too early. */
    private void awaitReachable(String host, int port) throws ProvisionException {
        long deadline = System.nanoTime() + readinessTimeoutSeconds * 1_000_000_000L;
        IOException last = null;
        while (System.nanoTime() < deadline) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 2000);
                return; // accepting connections
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ProvisionException("interrupted while waiting for the match server", ie);
                }
            }
        }
        throw new ProvisionException("match server did not accept connections on " + host + ":" + port
                + " within " + readinessTimeoutSeconds + "s" + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private void safeRemove(String containerId) {
        try {
            runDocker(List.of("docker", "rm", "-f", containerId), "remove container");
        } catch (ProvisionException e) {
            log.warning("[orchestration] failed to remove container " + containerId + ": " + e.getMessage());
        }
    }

    /** Runs a docker command, returning its combined stdout/stderr; throws on non-zero exit or timeout. */
    private String runDocker(List<String> command, String what) throws ProvisionException {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            if (!p.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new ProvisionException("docker timed out trying to " + what);
            }
            if (p.exitValue() != 0) {
                throw new ProvisionException("docker failed to " + what
                        + " (exit " + p.exitValue() + "): " + out.toString().trim());
            }
            return out.toString();
        } catch (IOException e) {
            throw new ProvisionException("could not exec docker to " + what + " (is the daemon reachable?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProvisionException("interrupted while trying to " + what, e);
        }
    }

    private static HostPort parseHostPort(String mapping) throws ProvisionException {
        // `docker port` can print several lines (ipv4 + ipv6); take the first.
        String first = mapping.lines().map(String::trim).filter(s -> !s.isEmpty()).findFirst().orElse("");
        int idx = first.lastIndexOf(':');
        if (idx < 0) throw new ProvisionException("unexpected `docker port` output: '" + mapping.trim() + "'");
        String host = first.substring(0, idx);
        // 0.0.0.0 / :: mean "all interfaces" and aren't routable; present as loopback to callers.
        if (host.isEmpty() || host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            host = "127.0.0.1";
        }
        try {
            return new HostPort(host, Integer.parseInt(first.substring(idx + 1).trim()));
        } catch (NumberFormatException e) {
            throw new ProvisionException("could not parse port from `docker port` output: '" + mapping.trim() + "'", e);
        }
    }

    /** Container names must match {@code [a-zA-Z0-9][a-zA-Z0-9_.-]+}; sanitise the match id for that use. */
    private static String dockerSafe(String s) {
        String cleaned = s.replaceAll("[^a-zA-Z0-9_.-]", "-");
        return cleaned.isEmpty() ? "x" : cleaned;
    }

    private record HostPort(String host, int port) {}
}
