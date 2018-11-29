package com.cloudbees.workflow.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Git {
    public static String lastCommit(Path directory) throws IOException, InterruptedException {
        return runCommand(directory, "git", "rev-parse", "--short", "HEAD");
    }

    public static void gitInit(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "init");
    }

    public static void gitStage(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "add", "-A");
    }

    public static void gitCommit(Path directory, String message) throws IOException, InterruptedException {
        runCommand(directory, "git", "commit", "-m", message);
    }

    public static void gitPush(Path directory) throws IOException, InterruptedException {
        runCommand(directory, "git", "push");
    }

    public static void gitClone(Path directory, String originUrl) throws IOException, InterruptedException {
        runCommand(directory.getParent(), "git", "clone", originUrl, directory.getFileName().toString());
    }

    public static String runCommand(Path directory, String... command) throws IOException, InterruptedException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.exists(directory)) {
            throw new RuntimeException("can't run command in non-existing directory '" + directory + "'");
        }
        ProcessBuilder pb = new ProcessBuilder()
            .command(command)
            .directory(directory.toFile());
        Process p = pb.start();
        StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");
        StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
        outputGobbler.start();
        errorGobbler.start();
        int exit = p.waitFor();
        errorGobbler.join();
        outputGobbler.join();
        if (exit != 0) {
            throw new AssertionError(String.format("runCommand returned %d", exit));
        }
        return outputGobbler.toString();
    }

    private static class StreamGobbler extends Thread {
        private final InputStream is;
        private final String type;
        private final StringBuilder b = new StringBuilder();

        private StreamGobbler(InputStream is, String type) {
            this.is = is;
            this.type = type;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is));) {
                String line;
                while ((line = br.readLine()) != null) {
                    b.append(line);
                    b.append('\n');
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        public String toString() {
            return b.toString().trim();
        }
    }

}