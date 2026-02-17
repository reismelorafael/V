package com.vectras.vm.core;

import android.content.Context;

import com.termux.app.TermuxService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProotCommandBuilder {

    public static class Options {
        public String home;
        public String user;
        public String term;
        public String tmpDir;
        public String shell;
        public String display;
        public String pulseServer;
        public String xdgRuntimeDir;
        public String path;
        public String sdlVideoDriver;
    }

    private final Context context;
    private final String rootfsPath;
    private final String workDir;
    private String filesDirPath;
    private String home = "/root";
    private String user = "root";
    private String term = "xterm-256color";
    private String tmpDir = "/tmp";
    private String shell = "/bin/sh";
    private String display;
    private String pulseServer;
    private String xdgRuntimeDir;
    private String path;
    private String sdlVideoDriver;

    public ProotCommandBuilder(Context context, String rootfsPath, String workDir) {
        this.context = context;
        this.rootfsPath = rootfsPath;
        this.workDir = workDir;
    }

    public ProotCommandBuilder setFilesDirPath(String filesDirPath) {
        this.filesDirPath = filesDirPath;
        return this;
    }

    public ProotCommandBuilder setHome(String home) {
        this.home = home;
        return this;
    }

    public ProotCommandBuilder setUser(String user) {
        this.user = user;
        return this;
    }

    public ProotCommandBuilder setTerm(String term) {
        this.term = term;
        return this;
    }

    public ProotCommandBuilder setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
        return this;
    }

    public ProotCommandBuilder setShell(String shell) {
        this.shell = shell;
        return this;
    }

    public ProotCommandBuilder setDisplay(String display) {
        this.display = display;
        return this;
    }

    public ProotCommandBuilder setPulseServer(String pulseServer) {
        this.pulseServer = pulseServer;
        return this;
    }

    public ProotCommandBuilder setXdgRuntimeDir(String xdgRuntimeDir) {
        this.xdgRuntimeDir = xdgRuntimeDir;
        return this;
    }

    public ProotCommandBuilder setPath(String path) {
        this.path = path;
        return this;
    }

    public ProotCommandBuilder setSdlVideoDriver(String sdlVideoDriver) {
        this.sdlVideoDriver = sdlVideoDriver;
        return this;
    }

    public void applyEnvironment(Map<String, String> environment) {
        Options options = new Options();
        options.home = home;
        options.user = user;
        options.term = term;
        options.tmpDir = tmpDir;
        options.shell = shell;
        options.display = display;
        options.pulseServer = pulseServer;
        options.xdgRuntimeDir = xdgRuntimeDir;
        options.path = path;
        options.sdlVideoDriver = sdlVideoDriver;
        applyDefaultEnv(environment, options);
    }

    public void applyDefaultEnv(ProcessBuilder processBuilder, Options options) {
        applyDefaultEnv(processBuilder.environment(), options);
    }

    public void applyDefaultEnv(Map<String, String> environment, Options options) {
        String filesDir = resolveFilesDirPath();
        Options resolved = options != null ? options : new Options();
        String resolvedTmpDir = firstNotBlank(resolved.tmpDir, tmpDir, "/tmp");
        String resolvedXdgRuntimeDir = firstNotBlank(resolved.xdgRuntimeDir, xdgRuntimeDir, resolvedTmpDir);

        environment.put("PROOT_TMP_DIR", filesDir + "/usr/tmp");
        putIfNotEmpty(environment, "HOME", firstNotBlank(resolved.home, home, "/root"));
        putIfNotEmpty(environment, "USER", firstNotBlank(resolved.user, user, "root"));
        putIfNotEmpty(environment, "TERM", firstNotBlank(resolved.term, term, "xterm-256color"));
        putIfNotEmpty(environment, "TMPDIR", resolvedTmpDir);
        putIfNotEmpty(environment, "SHELL", firstNotBlank(resolved.shell, shell, "/bin/sh"));
        putIfNotEmpty(environment, "DISPLAY", firstNotBlank(resolved.display, display));
        putIfNotEmpty(environment, "PULSE_SERVER", firstNotBlank(resolved.pulseServer, pulseServer));
        putIfNotEmpty(environment, "XDG_RUNTIME_DIR", resolvedXdgRuntimeDir);
        putIfNotEmpty(environment, "PATH", firstNotBlank(resolved.path, path));
        putIfNotEmpty(environment, "SDL_VIDEODRIVER", firstNotBlank(resolved.sdlVideoDriver, sdlVideoDriver));
    }

    public List<String> buildCommand() {
        String filesDir = resolveFilesDirPath();
        String devShmBind = filesDir + "/distro/root:/dev/shm";
        String tmpBind = filesDir + "/usr/tmp:/tmp";

        List<String> command = new ArrayList<>();
        command.add(TermuxService.PREFIX_PATH + "/bin/proot");
        command.add("--kill-on-exit");
        command.add("--link2symlink");
        command.add("-0");
        command.add("-r");
        command.add(rootfsPath);
        command.add("-b");
        command.add("/dev");
        command.add("-b");
        command.add("/proc");
        command.add("-b");
        command.add("/sys");
        command.add("-b");
        command.add("/sdcard");
        command.add("-b");
        command.add("/storage");
        command.add("-b");
        command.add("/data");
        command.add("-b");
        command.add(devShmBind);
        command.add("-b");
        command.add(tmpBind);
        command.add("-w");
        command.add(workDir);
        command.add("/bin/sh");
        command.add("--login");

        return command;
    }

    private String resolveFilesDirPath() {
        if (filesDirPath != null && !filesDirPath.trim().isEmpty()) {
            return filesDirPath;
        }
        return context.getFilesDir().getAbsolutePath();
    }

    private static void putIfNotEmpty(Map<String, String> environment, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            environment.put(key, value);
        }
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
