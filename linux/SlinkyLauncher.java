import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Console launcher for the same native bridge and Java payload used on Windows. */
public final class SlinkyLauncher {
    private static final long TIMEOUT_MS = 250000L;

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Exception error) {
            System.err.println("Could not start Slinky: " + safe(error.getMessage()));
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        String pid = null;
        boolean list = false;
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("Usage: ./slinky [--pid PID | --list | --help]");
            System.out.println("Start Minecraft 1.8.9 or Lunar first, then run this as the same user.");
            System.out.println("With no arguments, one recognised game is selected automatically.");
            return 0;
        }
        if (args.length == 1 && "--list".equals(args[0])) list = true;
        else if (args.length == 2 && "--pid".equals(args[0])) {
            if (!args[1].matches("[1-9][0-9]{0,9}")) {
                System.err.println("PID must be a positive number. Use ./slinky --list to find it.");
                return 2;
            }
            pid = args[1];
        } else if (args.length != 0) {
            System.err.println("Unknown options. Use ./slinky --help.");
            return 2;
        }
        if (!"Linux".equals(System.getProperty("os.name"))) {
            System.err.println("This launcher is for Linux. Use Slinky.exe on Windows.");
            return 2;
        }
        List<VirtualMachineDescriptor> processes = new ArrayList<>(VirtualMachine.list());
        processes.removeIf(vm -> vm.id().equals(Long.toString(ProcessHandle.current().pid())));
        processes.sort(Comparator.comparingLong(vm -> numericPid(vm.id())));
        if (list) {
            printProcesses(processes);
            return 0;
        }
        if (pid == null) {
            List<VirtualMachineDescriptor> games = new ArrayList<>();
            for (VirtualMachineDescriptor vm : processes) {
                if (isGame(vm.displayName())) games.add(vm);
            }
            if (games.size() != 1) {
                System.err.println(games.isEmpty() ? "No Minecraft/Lunar process was recognised." :
                    "More than one game is running; choose a PID.");
                printProcesses(processes);
                System.err.println("If your game is listed, use ./slinky --pid PID.");
                return 2;
            }
            pid = games.get(0).id();
        }
        Path root = Paths.get(System.getProperty("slinky.home", ".")).toRealPath();
        Path library = root.resolve("libSlinkyNative.so");
        if (!Files.isRegularFile(library)) throw new IOException("Missing libSlinkyNative.so beside the launcher.");
        for (int id = 101; id <= 103; id++) {
            if (!Files.isRegularFile(root.resolve("payload-" + id + ".jar")))
                throw new IOException("Missing payload-" + id + ".jar beside the launcher.");
        }
        Path runDirectory = Files.createTempDirectory("slinky-", PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rwx------"))).toRealPath();
        Path log = runDirectory.resolve("slinky-native.log");
        System.out.println("Starting Slinky in game PID " + pid + "...");
        System.out.println("Log: " + log);
        final String target = pid;
        // A daemon keeps even an unresponsive JVM attach from hanging the console indefinitely.
        final Throwable[] failure = new Throwable[1];
        final boolean[] completed = new boolean[1];
        final Object lock = new Object();
        Thread attach = new Thread(() -> {
            VirtualMachine vm = null;
            try {
                vm = VirtualMachine.attach(target);
                vm.loadAgentPath(library.toAbsolutePath().toString(), runDirectory.toString());
            } catch (Throwable error) {
                synchronized (lock) { failure[0] = error; }
            } finally {
                if (vm != null) try { vm.detach(); } catch (IOException ignored) { }
                synchronized (lock) { completed[0] = true; }
            }
        }, "slinky-attach");
        attach.setDaemon(true);
        attach.start();
        LogTail tail = new LogTail(log);
        long deadline = System.nanoTime() + TIMEOUT_MS * 1000000L;
        long nextProgress = System.nanoTime() + 10000000000L;
        while (System.nanoTime() < deadline) {
            String status = tail.poll(false);
            boolean done;
            Throwable error;
            synchronized (lock) { done = completed[0]; error = failure[0]; }
            if (status != null) return finish(status, log);
            if (done && error != null) {
                status = tail.poll(true);
                if (status != null) return finish(status, log);
                System.err.println("The game did not accept the launcher (" + error.getClass().getSimpleName() + ").");
                System.err.println("Run both as the same user, without sudo. Check that the game allows Java attach.");
                System.err.println("If its JVM disables attach, restart the game without -XX:+DisableAttachMechanism.");
                System.err.println("If it disables agent loading, enable -XX:+EnableDynamicAgentLoading in its JVM options.");
                System.err.println("Details: " + safe(error.getMessage()));
                System.err.println("Log: " + log);
                return 1;
            }
            if (System.nanoTime() >= nextProgress) {
                System.out.println("Still waiting for the game...");
                nextProgress = System.nanoTime() + 10000000000L;
            }
            Thread.sleep(100);
        }
        String status = tail.poll(true);
        if (status != null) return finish(status, log);
        System.err.println("The game did not report a result within 250 seconds. Log: " + log);
        return 1;
    }

    private static int finish(String status, Path log) {
        if ("RUNNING".equals(status) || "RELOADED".equals(status)) {
            System.out.println("Slinky is running. Press Right Shift in the game to open the menu.");
            return 0;
        }
        if ("ALREADY_RUNNING".equals(status)) {
            System.out.println("Slinky is already running. Press Right Shift to open the menu.");
            return 0;
        }
        System.err.println("Slinky could not start. The lines above explain where it stopped.");
        System.err.println("Full log: " + log);
        return 1;
    }

    private static void printProcesses(List<VirtualMachineDescriptor> processes) {
        if (processes.isEmpty()) System.out.println("No accessible Java processes found.");
        for (VirtualMachineDescriptor vm : processes)
            System.out.println("PID " + safe(vm.id()) + "  " + applicationName(vm.displayName()) +
                (isGame(vm.displayName()) ? "  (game candidate)" : ""));
    }

    private static long numericPid(String pid) {
        try { return Long.parseLong(pid); } catch (NumberFormatException ignored) { return Long.MAX_VALUE; }
    }

    private static String applicationName(String displayName) {
        // Never print VM arguments: game launch commands can include account tokens.
        String first = displayName == null ? "" : displayName.trim().split("\\s+", 2)[0];
        if (first.isEmpty()) return "Java application";
        String name = first.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.matches("[a-zA-Z0-9_$.-]{1,160}") || name.startsWith("-")) return "Java application";
        return name;
    }

    private static boolean isGame(String displayName) {
        String app = applicationName(displayName).toLowerCase(Locale.ROOT);
        return app.contains("minecraft") || app.contains("lunar") || app.startsWith("com.moonsworth.") ||
            app.equals("net.minecraft.launchwrapper.launch") || app.equals("lcp") || app.equals("lcp.jar");
    }

    private static String safe(String message) {
        if (message == null || message.isEmpty()) return "no additional details";
        String cleaned = message.replaceAll("[\\p{Cntrl}]", " ");
        return cleaned.length() > 600 ? cleaned.substring(0, 600) + "..." : cleaned;
    }

    private static final class LogTail {
        private final Path path;
        private long position;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private String finalStatus;
        LogTail(Path path) { this.path = path; }
        String poll(boolean flushPartial) throws IOException {
            if (Files.isRegularFile(path)) {
                try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
                    input.seek(position);
                    int value;
                    while ((value = input.read()) != -1) {
                        position++;
                        if (value == '\n') emit(); else pending.write(value);
                    }
                }
            }
            if (flushPartial && pending.size() != 0) emit();
            return finalStatus;
        }
        private void emit() {
            String line = new String(pending.toByteArray(), StandardCharsets.UTF_8).trim();
            pending.reset();
            if (line.contains("FINAL STATUS: ")) {
                String status = line.substring(line.indexOf("FINAL STATUS: ") + 14).trim();
                if (status.matches("RUNNING|RELOADED|ALREADY_RUNNING|FAILED")) finalStatus = status;
            }
            if (!line.isEmpty() && !line.contains("preserved existing ACC_STRICT constructor flag") &&
                !line.contains("is not prepared; retaining fresh modifiers")) System.out.println(safe(line));
        }
    }
}
