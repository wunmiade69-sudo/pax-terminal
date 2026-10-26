package com.paxterminal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.system.Os;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PAX Terminal 0.4.4. Runs a Debian ARM64 userspace under PRoot.
 * The environment remains inside this Android app's private sandbox.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 17, 14);
    private static final int PANEL = Color.rgb(13, 27, 22);
    private static final int PANEL_2 = Color.rgb(18, 35, 28);
    private static final int GREEN = Color.rgb(184, 243, 107);
    private static final int TEXT = Color.rgb(224, 239, 226);
    private static final int MUTED = Color.rgb(135, 164, 143);
    private static final int AMBER = Color.rgb(255, 190, 95);
    private static final String META_PREFIX = "__PAX_META__";
    private static final String GUEST_HOME = "/home/pax";
    private static final String ROOTFS_ASSET = "debian-rootfs-arm64.tar.gz";
    private static final int REQUEST_IMPORT_FILE = 501;
    private static final int REQUEST_EXPORT_FILE = 502;
    private static final int MAX_OUTPUT_CHARS = 120_000;
    private static final int MAX_PREVIEW_CHARS = 16_000;
    private static final int MAX_HISTORY = 100;

    private LinearLayout root;
    private TextView terminalOutput;
    private TextView shellStatusBadge;
    private ScrollView outputScroll;
    private EditText commandInput;
    private BufferedWriter shellWriter;
    private Process shellProcess;
    private volatile boolean shellAlive = false;
    private volatile String currentDirectory = "/";
    private volatile int lastExitCode = 0;
    private File homeDirectory;
    private File rootFsDirectory;
    private boolean environmentSetupInProgress = false;
    private File pendingExportFile;
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15 enforces edge-to-edge for apps targeting API 35+.
            getWindow().setDecorFitsSystemWindows(false);
        }
        buildInterface();
        startShell();
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(
                        dp(12) + insets.getSystemWindowInsetLeft(),
                        dp(8) + insets.getSystemWindowInsetTop(),
                        dp(12) + insets.getSystemWindowInsetRight(),
                        dp(8) + insets.getSystemWindowInsetBottom());
                return insets;
            });
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), dp(7), dp(4), dp(9));

        LinearLayout brandStack = new LinearLayout(this);
        brandStack.setOrientation(LinearLayout.VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("PAX TERMINAL");
        brand.setTextColor(GREEN);
        brand.setTextSize(20);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        TextView subtitle = new TextView(this);
        subtitle.setText("DEBIAN ARM64 DEV WORKSPACE  /  v0.4.4");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(9);
        subtitle.setLetterSpacing(0.07f);
        brandStack.addView(brand);
        brandStack.addView(subtitle);
        header.addView(brandStack, new LinearLayout.LayoutParams(0, -2, 1f));

        shellStatusBadge = new TextView(this);
        shellStatusBadge.setText("STARTING SHELL");
        shellStatusBadge.setTextColor(GREEN);
        shellStatusBadge.setTextSize(9);
        shellStatusBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        shellStatusBadge.setPadding(dp(9), dp(8), dp(9), dp(8));
        shellStatusBadge.setBackground(roundRect(PANEL_2, dp(10)));
        shellStatusBadge.setOnClickListener(v -> showMoreMenu());
        header.addView(shellStatusBadge);
        root.addView(header);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(39, 64, 48));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setBackground(roundRect(Color.rgb(5, 13, 10), dp(12)));
        outputScroll.setClipToPadding(false);
        outputScroll.setPadding(dp(4), dp(8), dp(4), dp(8));

        terminalOutput = new TextView(this);
        terminalOutput.setTextColor(TEXT);
        terminalOutput.setTextSize(12.5f);
        terminalOutput.setTypeface(Typeface.MONOSPACE);
        terminalOutput.setLineSpacing(dp(2), 1.0f);
        terminalOutput.setPadding(dp(8), dp(4), dp(8), dp(12));
        terminalOutput.setTextIsSelectable(true);
        outputScroll.addView(terminalOutput, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams outputParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        outputParams.topMargin = dp(9);
        outputParams.bottomMargin = dp(9);
        root.addView(outputScroll, outputParams);

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        quickActions.setGravity(Gravity.CENTER_VERTICAL);
        quickActions.addView(makeActionButton("HELP", v -> { showHelp(); showPrompt(); }), weightButton());
        quickActions.addView(makeActionButton("CLEAR", v -> { clearTerminal(); showPrompt(); }), spacedWeight());
        quickActions.addView(makeActionButton("FILES", v -> showFileBrowser()), spacedWeight());
        quickActions.addView(makeActionButton("MORE", v -> showMoreMenu()), spacedWeight());
        root.addView(quickActions);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(8), dp(4), dp(4), dp(4));
        inputRow.setBackground(roundRect(PANEL, dp(12)));

        commandInput = new EditText(this);
        commandInput.setSingleLine(true);
        commandInput.setTextColor(TEXT);
        commandInput.setHintTextColor(MUTED);
        commandInput.setHint("Enter a shell command…");
        commandInput.setTextSize(13);
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setPadding(dp(8), dp(8), dp(8), dp(8));
        commandInput.setBackgroundColor(Color.TRANSPARENT);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        commandInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitCommand();
                return true;
            }
            return false;
        });
        commandInput.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                moveHistory(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                moveHistory(1);
                return true;
            }
            return false;
        });
        inputRow.addView(commandInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button run = new Button(this);
        run.setText("RUN  ›");
        run.setTextColor(BG);
        run.setTextSize(12);
        run.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        run.setAllCaps(false);
        run.setBackground(roundRect(GREEN, dp(10)));
        run.setPadding(dp(12), 0, dp(12), 0);
        run.setMinWidth(dp(68));
        run.setOnClickListener(v -> submitCommand());
        inputRow.addView(run, new LinearLayout.LayoutParams(-2, dp(42)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(8);
        root.addView(inputRow, inputParams);

        TextView footer = new TextView(this);
        footer.setText("DEBIAN ARM64  •  NODE.JS / PYTHON / GIT  •  ROOTLESS LINUX");
        footer.setTextColor(MUTED);
        footer.setTextSize(8);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(2), dp(9), dp(2), dp(2));
        root.addView(footer);

        setContentView(root);
    }

    private LinearLayout.LayoutParams weightButton() {
        return new LinearLayout.LayoutParams(0, dp(38), 1f);
    }

    private LinearLayout.LayoutParams spacedWeight() {
        LinearLayout.LayoutParams p = weightButton();
        p.leftMargin = dp(7);
        return p;
    }

    private Button makeActionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(GREEN);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(roundRect(PANEL_2, dp(9)));
        button.setOnClickListener(listener);
        return button;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void startShell() {
        if (environmentSetupInProgress) return;
        if (shellAlive && shellProcess != null && shellProcess.isAlive()) return;

        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) { arm64 = true; break; }
        }
        if (!arm64) {
            appendOutput("PAX Terminal v0.4.4 currently requires a 64-bit ARM Android device (arm64-v8a).\n");
            updateShellBadge("UNSUPPORTED DEVICE", AMBER);
            return;
        }

        homeDirectory = new File(getFilesDir(), "home");
        rootFsDirectory = new File(getFilesDir(), "debian-rootfs");
        if (!homeDirectory.exists() && !homeDirectory.mkdirs()) {
            appendOutput("Could not create the PAX workspace directory.\n");
            updateShellBadge("WORKSPACE ERROR", AMBER);
            return;
        }

        File proot = new File(getApplicationInfo().nativeLibraryDir, "libproot-userland.so");
        File loader = new File(getApplicationInfo().nativeLibraryDir, "libproot-loader.so");
        if (!proot.isFile() || !loader.isFile()) {
            appendOutput("The Linux runtime was not included in this build. Build the APK with the PAX Terminal GitHub Actions workflow.\n");
            updateShellBadge("RUNTIME MISSING", AMBER);
            return;
        }

        if (!new File(rootFsDirectory, ".pax-ready").isFile()) {
            environmentSetupInProgress = true;
            updateShellBadge("PREPARING LINUX", AMBER);
            appendOutput("Preparing the Debian ARM64 environment for first use. This step runs once and may take a little while.\n\n");
            fileExecutor.execute(() -> {
                try {
                    bootstrapLinuxEnvironment();
                    runOnUiThread(() -> {
                        environmentSetupInProgress = false;
                        if (!isFinishing() && !isDestroyed()) launchLinuxShell();
                    });
                } catch (Exception exception) {
                    deleteRecursively(rootFsDirectory);
                    runOnUiThread(() -> {
                        environmentSetupInProgress = false;
                        updateShellBadge("SETUP FAILED", AMBER);
                        appendOutput("[Could not prepare the Linux environment]\n" + safeMessage(exception) + "\n");
                        appendOutput("Check that the APK was built by the repository workflow and has enough free storage, then reopen PAX Terminal.\n");
                    });
                }
            });
            return;
        }

        launchLinuxShell();
    }

    private void launchLinuxShell() {
        try {
            if (homeDirectory == null) homeDirectory = new File(getFilesDir(), "home");
            if (rootFsDirectory == null) rootFsDirectory = new File(getFilesDir(), "debian-rootfs");
            File proot = new File(getApplicationInfo().nativeLibraryDir, "libproot-userland.so");
            if (!proot.isFile()) throw new IllegalStateException("PRoot runtime is missing from this APK.");

            List<String> command = new ArrayList<>();
            command.add(proot.getAbsolutePath());
            command.add("-0");
            command.add("--link2symlink");
            command.add("-r");
            command.add(rootFsDirectory.getAbsolutePath());
            command.add("-b"); command.add("/dev:/dev");
            command.add("-b"); command.add("/proc:/proc");
            command.add("-b"); command.add("/sys:/sys");
            command.add("-b"); command.add(homeDirectory.getAbsolutePath() + ":" + GUEST_HOME);
            command.add("-b"); command.add(homeDirectory.getAbsolutePath() + ":/workspace");
            command.add("-w"); command.add(GUEST_HOME);
            command.add("/bin/bash");
            command.add("--noprofile");
            command.add("--norc");

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(getApplicationInfo().nativeLibraryDir));
            builder.redirectErrorStream(true);
            Map<String, String> env = builder.environment();
            env.put("HOME", GUEST_HOME);
            env.put("PWD", GUEST_HOME);
            env.put("USER", "pax");
            env.put("LOGNAME", "pax");
            env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            env.put("TERM", "dumb");
            env.put("LANG", "C.UTF-8");
            env.put("TMPDIR", "/tmp");
            Process startedProcess = builder.start();
            shellProcess = startedProcess;
            shellWriter = new BufferedWriter(new OutputStreamWriter(startedProcess.getOutputStream(), StandardCharsets.UTF_8));
            shellAlive = true;
            currentDirectory = GUEST_HOME;
            lastExitCode = 0;
            updateShellBadge("LINUX ACTIVE", GREEN);

            Thread outputThread = new Thread(() -> readShellOutput(startedProcess), "pax-linux-output");
            outputThread.setDaemon(true);
            outputThread.start();

            appendOutput("PAX TERMINAL  /  v0.4.4\n");
            appendOutput("Debian GNU/Linux ARM64 workspace running under PRoot.\n");
            appendOutput("Workspace: " + GUEST_HOME + "  |  Package command: pkg\n");
            appendOutput("Try: pkg update  |  pkg install nodejs npm pnpm python git deno build-essential\n");
            appendOutput("Runtimes: node  |  npm  |  pnpm  |  python  |  deno  |  bun  |  git\n");
            appendOutput("Developer commands: npm install  |  npm run dev  |  pnpm dev  |  deno task dev  |  npx tsc --noEmit\n");
            appendOutput("Shared project folder: /home/pax and /workspace\n\n");
            showPrompt();
        } catch (Exception exception) {
            shellAlive = false;
            updateShellBadge("SHELL ERROR", AMBER);
            appendOutput("[Could not start the Linux shell]\n" + safeMessage(exception) + "\n");
        }
    }

    private void bootstrapLinuxEnvironment() throws Exception {
        synchronized (this) {
            if (rootFsDirectory == null) rootFsDirectory = new File(getFilesDir(), "debian-rootfs");
            File marker = new File(rootFsDirectory, ".pax-ready");
            if (marker.isFile()) return;
            deleteRecursively(rootFsDirectory);
            if (!rootFsDirectory.mkdirs() && !rootFsDirectory.isDirectory()) {
                throw new IllegalStateException("Could not create the Linux root directory.");
            }

            Map<String, String> hardLinks = new LinkedHashMap<>();
            Map<String, Integer> directoryModes = new LinkedHashMap<>();
            Map<String, String> symbolicLinks = new LinkedHashMap<>();
            Path rootPath = rootFsDirectory.getAbsoluteFile().toPath().normalize();
            try (InputStream raw = getAssets().open(ROOTFS_ASSET);
                 GzipCompressorInputStream gzip = new GzipCompressorInputStream(raw);
                 TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
                TarArchiveEntry entry;
                byte[] buffer = new byte[32 * 1024];
                long totalWritten = 0;
                while ((entry = tar.getNextTarEntry()) != null) {
                    String entryName = entry.getName();
                    while (entryName.startsWith("/")) entryName = entryName.substring(1);
                    if (entryName.isEmpty() || entryName.equals(".")) continue;
                    Path entryPath = rootPath.resolve(entryName).normalize();
                    if (!entryPath.startsWith(rootPath)) {
                        throw new IllegalStateException("Unsafe path in Linux archive: " + entry.getName());
                    }
                    File target = entryPath.toFile();
                    String targetAbsolute = target.getAbsolutePath();
                    if (entry.isSymbolicLink()) {
                        symbolicLinks.put(targetAbsolute, entry.getLinkName());
                    } else if (entry.isDirectory()) {
                        if (!target.exists() && !target.mkdirs() && !target.isDirectory()) {
                            throw new IllegalStateException("Could not create directory: " + entryName);
                        }
                        directoryModes.put(targetAbsolute, entry.getMode());
                    } else if (entry.isLink()) {
                        hardLinks.put(targetAbsolute, entry.getLinkName());
                    } else if (entry.isFile()) {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                            throw new IllegalStateException("Could not create directory for " + entryName);
                        }
                        try (OutputStream output = new FileOutputStream(target)) {
                            int count;
                            while ((count = tar.read(buffer)) != -1) {
                                output.write(buffer, 0, count);
                                totalWritten += count;
                                if (totalWritten > 1_500_000_000L) throw new IllegalStateException("Linux archive expanded beyond the safety limit.");
                            }
                        }
                        applyTarMode(target, entry.getMode(), false);
                    }
                    // Device nodes and unsupported special entries are supplied by Android bind mounts.
                }
            }

            for (Map.Entry<String, String> link : hardLinks.entrySet()) {
                Path destinationPath = new File(link.getKey()).getAbsoluteFile().toPath().normalize();
                String linkName = link.getValue();
                while (linkName.startsWith("/")) linkName = linkName.substring(1);
                Path sourcePath = rootPath.resolve(linkName).normalize();
                if (!destinationPath.startsWith(rootPath) || !sourcePath.startsWith(rootPath)) {
                    throw new IllegalStateException("Invalid hardlink path in Linux archive: " + link.getValue());
                }
                File source = sourcePath.toFile();
                File destination = destinationPath.toFile();
                if (!source.isFile() || isSymbolicLink(source)) {
                    throw new IllegalStateException("Invalid hardlink target in Linux archive: " + link.getValue());
                }
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                deleteRecursively(destination);
                Os.link(source.getAbsolutePath(), destination.getAbsolutePath());
            }

            // Symbolic links are created after regular files, so archive paths never write
            // through a link into Android's host filesystem while files are being extracted.
            for (Map.Entry<String, String> link : symbolicLinks.entrySet()) {
                File target = new File(link.getKey());
                Path targetPath = target.getAbsoluteFile().toPath().normalize();
                if (!targetPath.startsWith(rootPath)) {
                    throw new IllegalStateException("Unsafe symbolic link path in Linux archive: " + link.getKey());
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                deleteRecursively(target);
                Os.symlink(link.getValue(), target.getAbsolutePath());
            }

            // Preserve archive permissions after extraction. Never chmod through a symlink
            // ancestor, which could otherwise resolve outside the root directory on Android.
            List<Map.Entry<String, Integer>> directoryEntries = new ArrayList<>(directoryModes.entrySet());
            directoryEntries.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
            for (Map.Entry<String, Integer> directory : directoryEntries) {
                File target = new File(directory.getKey());
                if (target.isDirectory() && !hasSymlinkAncestor(target, rootPath)) {
                    applyTarMode(target, directory.getValue(), true);
                }
            }

            File[] neededDirectories = {
                    new File(rootFsDirectory, "dev"), new File(rootFsDirectory, "proc"),
                    new File(rootFsDirectory, "sys"), new File(rootFsDirectory, "tmp"),
                    new File(rootFsDirectory, "root"), new File(rootFsDirectory, "home/pax"),
                    new File(rootFsDirectory, "workspace"), new File(rootFsDirectory, "usr/local/bin"),
                    new File(rootFsDirectory, "etc/profile.d")
            };
            for (File directory : neededDirectories) {
                if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
                    throw new IllegalStateException("Could not create Linux directory: " + directory.getName());
                }
            }
            applyTarMode(new File(rootFsDirectory, "tmp"), 01777, true);
            writeRuntimeFile("etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n");
            writeRuntimeFile("etc/resolv.conf", resolverConfig());
            writeRuntimeFile("etc/profile.d/pax-terminal.sh", "export HOME=/home/pax\nexport USER=pax\nexport LOGNAME=pax\nexport LANG=C.UTF-8\nexport PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n");
            writeRuntimeFile("usr/local/bin/pkg", pkgWrapperScript());
            applyTarMode(new File(rootFsDirectory, "usr/local/bin/pkg"), 0755, false);
            writeRuntimeFile("usr/local/bin/pax-bg", paxBackgroundScript());
            writeRuntimeFile("usr/local/bin/pax-jobs", paxJobsScript());
            writeRuntimeFile("usr/local/bin/pax-stop", paxStopScript());
            writeRuntimeFile("usr/local/bin/pax-logs", paxLogsScript());
            applyTarMode(new File(rootFsDirectory, "usr/local/bin/pax-bg"), 0755, false);
            applyTarMode(new File(rootFsDirectory, "usr/local/bin/pax-jobs"), 0755, false);
            applyTarMode(new File(rootFsDirectory, "usr/local/bin/pax-stop"), 0755, false);
            applyTarMode(new File(rootFsDirectory, "usr/local/bin/pax-logs"), 0755, false);
            writeRuntimeFile(".pax-ready", "PAX Terminal Debian ARM64 rootfs ready\n");
        }
    }

    private String pkgWrapperScript() {
        return "#!/bin/bash\n" +
                "set -e\n" +
                "action=\"${1:-help}\"\n" +
                "if [[ $# -gt 0 ]]; then shift; fi\n" +
                "case \"$action\" in\n" +
                "  update) exec apt-get update \"$@\" ;;\n" +
                "  upgrade) exec apt-get upgrade -y \"$@\" ;;\n" +
                "  install)\n" +
                "    packages=()\n" +
                "    for name in \"$@\"; do\n" +
                "      case \"$name\" in\n" +
                "        python) packages+=(python3) ;;\n" +
                "        pip|python-pip) packages+=(python3-pip) ;;\n" +
                "        node) packages+=(nodejs) ;;\n" +
                "        nodejs-lts) packages+=(nodejs) ;;\n" +
                "        typescript) packages+=(node-typescript) ;;\n" +
                "        openssh) packages+=(openssh-client) ;;\n" +
                "        sdk) packages+=(android-sdk) ;;\n" +
                "        android-tools) packages+=(android-sdk-platform-tools) ;;\n" +
                "        openjdk) packages+=(default-jdk-headless) ;;\n" +
                "        *) packages+=(\"$name\") ;;\n" +
                "      esac\n" +
                "    done\n" +
                "    exec apt-get install -y \"${packages[@]}\" ;;\n" +
                "  remove|uninstall) exec apt-get remove -y \"$@\" ;;\n" +
                "  reinstall) exec apt-get install --reinstall -y \"$@\" ;;\n" +
                "  search) exec apt-cache search \"$@\" ;;\n" +
                "  show) exec apt-cache show \"$@\" ;;\n" +
                "  list-installed) exec dpkg-query -W -f='${Package}\\t${Version}\\n' ;;\n" +
                "  clean) exec apt-get clean ;;\n" +
                "  help|-h|--help) echo 'Usage: pkg {update|upgrade|install|remove|reinstall|search|show|list-installed|clean} [packages...]'; echo 'Packages come from Debian repositories.' ;;\n" +
                "  *) echo \"pkg: unknown action: $action\" >&2; exit 2 ;;\n" +
                "esac\n";
    }

    private String paxBackgroundScript() {
        return "#!/bin/bash\n" +
                "set -euo pipefail\n" +
                "if [[ $# -lt 2 ]]; then echo 'Usage: pax-bg NAME COMMAND [ARGS...]' >&2; exit 2; fi\n" +
                "name=\"$1\"; shift\n" +
                "safe_name=\"$(printf '%s' \"$name\" | tr -c 'A-Za-z0-9_.-' '_')\"\n" +
                "job_dir=\"$HOME/.pax-jobs\"; mkdir -p \"$job_dir\"\n" +
                "pid_file=\"$job_dir/$safe_name.pid\"; log_file=\"$job_dir/$safe_name.log\"\n" +
                "if [[ -s \"$pid_file\" ]] && kill -0 \"$(cat \"$pid_file\")\" 2>/dev/null; then echo \"Job already running: $safe_name (PID $(cat \"$pid_file\"))\"; exit 1; fi\n" +
                "nohup \"$@\" >\"$log_file\" 2>&1 </dev/null &\n" +
                "pid=$!; printf '%s\\n' \"$pid\" >\"$pid_file\"\n" +
                "echo \"Started $safe_name (PID $pid); log: $log_file\"\n";
    }

    private String paxJobsScript() {
        return "#!/bin/bash\n" +
                "set -u\n" +
                "dir=\"$HOME/.pax-jobs\"\n" +
                "if [[ ! -d \"$dir\" ]]; then echo 'No background jobs recorded.'; exit 0; fi\n" +
                "shopt -s nullglob\n" +
                "found=0\n" +
                "for pf in \"$dir\"/*.pid; do found=1; name=\"$(basename \"$pf\" .pid)\"; pid=\"$(cat \"$pf\")\"; if kill -0 \"$pid\" 2>/dev/null; then printf 'RUNNING  %-18s PID %s\\n' \"$name\" \"$pid\"; else printf 'STOPPED  %-18s PID %s\\n' \"$name\" \"$pid\"; fi; done\n" +
                "if [[ \"$found\" -eq 0 ]]; then echo 'No background jobs recorded.'; fi\n";
    }

    private String paxStopScript() {
        return "#!/bin/bash\n" +
                "set -euo pipefail\n" +
                "if [[ $# -ne 1 ]]; then echo 'Usage: pax-stop NAME' >&2; exit 2; fi\n" +
                "safe_name=\"$(printf '%s' \"$1\" | tr -c 'A-Za-z0-9_.-' '_')\"\n" +
                "pid_file=\"$HOME/.pax-jobs/$safe_name.pid\"\n" +
                "if [[ ! -s \"$pid_file\" ]]; then echo \"No recorded job named $safe_name\" >&2; exit 1; fi\n" +
                "pid=\"$(cat \"$pid_file\")\"\n" +
                "if kill -0 \"$pid\" 2>/dev/null; then kill -TERM \"$pid\"; echo \"Sent stop signal to $safe_name (PID $pid).\"; else echo \"$safe_name is already stopped.\"; fi\n" +
                "rm -f \"$pid_file\"\n";
    }

    private String paxLogsScript() {
        return "#!/bin/bash\n" +
                "set -euo pipefail\n" +
                "if [[ $# -ne 1 ]]; then echo 'Usage: pax-logs NAME' >&2; exit 2; fi\n" +
                "safe_name=\"$(printf '%s' \"$1\" | tr -c 'A-Za-z0-9_.-' '_')\"\n" +
                "log_file=\"$HOME/.pax-jobs/$safe_name.log\"\n" +
                "if [[ ! -f \"$log_file\" ]]; then echo \"No log found for $safe_name\" >&2; exit 1; fi\n" +
                "tail -n 100 \"$log_file\"\n";
    }

    private String resolverConfig() {
        StringBuilder result = new StringBuilder();
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            LinkProperties properties = manager == null || network == null ? null : manager.getLinkProperties(network);
            if (properties != null) {
                for (InetAddress address : properties.getDnsServers()) {
                    if (address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        result.append("nameserver ").append(address.getHostAddress()).append('\n');
                    }
                }
            }
        } catch (Exception ignored) { }
        if (result.length() == 0) result.append("nameserver 1.1.1.1\nnameserver 8.8.8.8\n");
        result.append("options timeout:2 attempts:2\n");
        return result.toString();
    }

    private void writeRuntimeFile(String relativePath, String content) throws Exception {
        File lexicalTarget = new File(rootFsDirectory, relativePath);
        File parent = lexicalTarget.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("Could not create directory for " + relativePath);
        }
        File canonicalParent = parent == null ? rootFsDirectory.getCanonicalFile() : parent.getCanonicalFile();
        String base = rootFsDirectory.getCanonicalPath();
        if (!canonicalParent.getPath().startsWith(base + File.separator) && !canonicalParent.getPath().equals(base)) {
            throw new IllegalStateException("Invalid runtime path: " + relativePath);
        }
        File target = new File(canonicalParent, lexicalTarget.getName());
        deleteRecursively(target);
        try (OutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void applyTarMode(File target, int mode, boolean directory) {
        int permissions = mode & 07777;
        if (permissions == 0) permissions = directory ? 0755 : 0644;
        try { Os.chmod(target.getAbsolutePath(), permissions); }
        catch (Exception ignored) { target.setReadable((permissions & 0444) != 0, false); target.setWritable((permissions & 0222) != 0, false); target.setExecutable((permissions & 0111) != 0, false); }
    }

    private boolean hasSymlinkAncestor(File target, Path rootPath) {
        Path current = target.getAbsoluteFile().toPath().normalize();
        while (current != null && current.startsWith(rootPath)) {
            if (isSymbolicLink(current.toFile())) return true;
            if (current.equals(rootPath)) break;
            current = current.getParent();
        }
        return false;
    }

    private boolean isSymbolicLink(File target) {
        if (target == null) return false;
        try { Os.readlink(target.getAbsolutePath()); return true; }
        catch (Exception ignored) { return false; }
    }

    private void deleteRecursively(File target) {
        if (target == null) return;
        boolean symlink = false;
        try { Os.readlink(target.getAbsolutePath()); symlink = true; }
        catch (Exception ignored) { }
        if (!target.exists() && !symlink) return;
        if (!symlink && target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        try { Os.remove(target.getAbsolutePath()); }
        catch (Exception ignored) { target.delete(); }
    }

    private void readShellOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(META_PREFIX)) {
                    String meta = line.substring(META_PREFIX.length());
                    int divider = meta.lastIndexOf('|');
                    if (divider > 0) {
                        String newPath = meta.substring(0, divider);
                        int exitCode = parseExitCode(meta.substring(divider + 1));
                        currentDirectory = newPath;
                        lastExitCode = exitCode;
                        runOnUiThread(() -> {
                            if (shellProcess != process || isFinishing()) return;
                            updateShellBadge(exitCode == 0 ? "SHELL ACTIVE" : "EXIT CODE " + exitCode,
                                    exitCode == 0 ? GREEN : AMBER);
                            showPrompt();
                        });
                    }
                } else {
                    appendOutput(line + "\n");
                }
            }
        } catch (Exception exception) {
            if (shellProcess == process && !isFinishing()) {
                appendOutput("[Shell output ended: " + safeMessage(exception) + "]\n");
            }
        } finally {
            if (shellProcess == process) {
                shellAlive = false;
                runOnUiThread(() -> {
                    if (shellProcess != process || isFinishing()) return;
                    updateShellBadge("SHELL STOPPED", AMBER);
                    appendOutput("\n[Shell stopped. Run another command to start a fresh shell.]\n");
                    showPrompt();
                });
            }
        }
    }

    private int parseExitCode(String text) {
        try { return Integer.parseInt(text.trim()); }
        catch (Exception ignored) { return 0; }
    }

    private void submitCommand() {
        if (commandInput == null) return;
        if (environmentSetupInProgress) {
            appendOutput("[Linux environment is still being prepared. Please wait for LINUX ACTIVE.]");
            return;
        }
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) return;
        commandInput.setText("");
        hideKeyboard();
        rememberCommand(command);

        if (isBuiltIn(command)) {
            appendOutput(promptText() + command + "\n");
            runBuiltIn(command);
            return;
        }

        if (!shellAlive || shellProcess == null || !shellProcess.isAlive()) startShell();
        if (!shellAlive || shellWriter == null) {
            appendOutput("[Shell is unavailable. Try MORE → Restart shell, or reopen the app.]\n");
            return;
        }
        appendOutput(promptText() + command + "\n");
        try {
            shellWriter.write(command);
            shellWriter.newLine();
            shellWriter.write("__pax_status=$?; printf '\\n" + META_PREFIX + "%s|%s\\n' \"$PWD\" \"$__pax_status\"");
            shellWriter.newLine();
            shellWriter.flush();
        } catch (Exception exception) {
            shellAlive = false;
            updateShellBadge("SHELL ERROR", AMBER);
            appendOutput("[Could not send command] " + safeMessage(exception) + "\n");
        }
    }

    private boolean isBuiltIn(String command) {
        return command.equals("help") || command.equals("pax-help")
                || command.equals("clear") || command.equals("cls")
                || command.equals("history") || command.equals("pax-files")
                || command.equals("files") || command.equals("pax-about")
                || command.equals("pax-version");
    }

    private void runBuiltIn(String command) {
        if (command.equals("help") || command.equals("pax-help")) {
            showHelp();
            showPrompt();
        } else if (command.equals("clear") || command.equals("cls")) {
            clearTerminal();
            showPrompt();
        } else if (command.equals("history")) {
            appendOutput("COMMAND HISTORY (latest first is not implied; ↑ / ↓ navigates)\n");
            int start = Math.max(0, commandHistory.size() - MAX_HISTORY);
            for (int i = start; i < commandHistory.size(); i++) {
                appendOutput(String.format(java.util.Locale.ROOT, "%3d  %s\n", i + 1, commandHistory.get(i)));
            }
            appendOutput("\n");
            showPrompt();
        } else if (command.equals("pax-files") || command.equals("files")) {
            showFileBrowser();
            showPrompt();
        } else {
            appendOutput("PAX Terminal v0.4.4 — Debian ARM64 developer environment.\n\n");
            showPrompt();
        }
    }

    private void rememberCommand(String command) {
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            if (commandHistory.size() > MAX_HISTORY) commandHistory.remove(0);
        }
        historyIndex = commandHistory.size();
    }

    private void moveHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        historyIndex = Math.max(0, Math.min(commandHistory.size(), historyIndex + direction));
        String value = historyIndex >= commandHistory.size() ? "" : commandHistory.get(historyIndex);
        commandInput.setText(value);
        commandInput.setSelection(commandInput.length());
    }

    private void showHelp() {
        appendOutput("PAX TERMINAL HELP  /  v0.4.4\n");
        appendOutput("────────────────────────────────────────────\n");
        appendOutput("Shell: pwd, ls -la, cd, find, grep, cat, echo, date, id\n");
        appendOutput("Packages: pkg update  |  pkg install nodejs npm pnpm git python deno build-essential\n");
        appendOutput("Node.js: node --version  |  npm install  |  npm run dev  |  npm test\n");
        appendOutput("pnpm: pnpm install  |  pnpm dev  |  pnpm test\n");
        appendOutput("Deno: deno --version  |  deno run main.ts  |  deno task dev  |  deno test\n");
        appendOutput("Bun: bun --version  |  bun install  |  bun run dev  |  bun test\n");
        appendOutput("Background server: pax-bg dev npm run dev -- --host 0.0.0.0\n");
        appendOutput("Jobs/logs: pax-jobs  |  pax-logs dev  |  pax-stop dev\n");
        appendOutput("TypeScript: tsc --version  |  npx tsc --noEmit  |  tsx script.ts\n");
        appendOutput("Python: python --version  |  python -m venv .venv  |  python -m unittest\n");
        appendOutput("Android SDK: sdkmanager --list  |  adb version (package availability varies)\n");
        appendOutput("Java: pkg install default-jdk-headless gradle\n");
        appendOutput("Git: git status  |  git clone <repository-url>\n");
        appendOutput("PAX helpers: help, clear, history, files, pax-about\n");
        appendOutput("Use ↑ / ↓ on a hardware keyboard to browse command history.\n");
        appendOutput("FILES browses /home/pax and /workspace (the same shared folder). MORE imports or exports files.\n\n");
        appendOutput("ENVIRONMENT NOTES\n");
        appendOutput("• Commands run in a Debian ARM64 userspace under PRoot.\n");
        appendOutput("• Included tools: Node.js/npm, pnpm, TypeScript/tsx, Python, Deno, Bun, Git and Java build utilities.\n");
        appendOutput("• pkg is a compatibility command backed by Debian apt; use Debian package names.\n");
        appendOutput("• This build uses line-oriented input rather than a full PTY; use pax-bg/pax-jobs/pax-logs/pax-stop for background services.\n");
        appendOutput("• The Android app remains sandboxed; simulated Linux root is not Android root.\n");
        appendOutput("• Debian SDK packages can be installed with pkg; current Android builds are most dependable through GitHub Actions.\n\n");
    }

    private void showMoreMenu() {
        String[] actions = {
                "Go to workspace home",
                "Import a file into workspace",
                "Export a workspace file",
                "Stop current process / restart shell",
                "About PAX Terminal"
        };
        new AlertDialog.Builder(this)
                .setTitle("PAX TERMINAL · TOOLS")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) executeCommand("cd \"$HOME\"");
                    else if (which == 1) beginImportFile();
                    else if (which == 2) showHomeFilesForExport();
                    else if (which == 3) restartShell();
                    else showAbout();
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("PAX Terminal v0.4.4")
                .setMessage("A native Android terminal with a Debian ARM64 environment, Node.js, npm, Git, Python 3 and native build tools. The shared workspace is exposed at /home/pax and /workspace.\n\nThe pkg command uses Debian apt repositories and package names. PRoot provides a rootless Linux environment; it does not grant Android root access. This version uses line-oriented input rather than a full PTY, so full-screen interactive applications are not guaranteed. Use pax-bg, pax-jobs, pax-logs and pax-stop to run and manage background services. Modern Android APK builds are most reliable through the included GitHub Actions workflow.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showFileBrowser() {
        if (homeDirectory == null) homeDirectory = new File(getFilesDir(), "home");
        String guestPath = currentDirectory;
        String relative;
        if (guestPath != null && (guestPath.equals(GUEST_HOME) || guestPath.startsWith(GUEST_HOME + "/"))) {
            relative = guestPath.equals(GUEST_HOME) ? "" : guestPath.substring(GUEST_HOME.length() + 1);
        } else if (guestPath != null && (guestPath.equals("/workspace") || guestPath.startsWith("/workspace/"))) {
            relative = guestPath.equals("/workspace") ? "" : guestPath.substring("/workspace/".length());
        } else {
            guestPath = GUEST_HOME;
            relative = "";
        }
        File directory;
        try { directory = new File(homeDirectory, relative).getCanonicalFile(); }
        catch (Exception exception) { directory = homeDirectory; }
        String homeCanonical;
        try { homeCanonical = homeDirectory.getCanonicalPath(); }
        catch (Exception exception) { homeCanonical = homeDirectory.getAbsolutePath(); }
        if (!directory.getPath().equals(homeCanonical) && !directory.getPath().startsWith(homeCanonical + File.separator)) {
            directory = homeDirectory;
        }
        if (!directory.isDirectory() || !directory.canRead()) {
            Toast.makeText(this, "This workspace folder cannot be read.", Toast.LENGTH_SHORT).show();
            return;
        }
        File[] listed = directory.listFiles();
        if (listed == null) listed = new File[0];
        Arrays.sort(listed, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        List<File> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        File parent = directory.getParentFile();
        if (parent != null && parent.getPath().startsWith(homeCanonical) && !directory.getPath().equals(homeCanonical)) {
            entries.add(parent);
            labels.add("↰  .. (parent folder)");
        }
        for (File item : listed) {
            if (!item.canRead()) continue;
            entries.add(item);
            if (item.isDirectory()) labels.add("▸  " + item.getName() + "/");
            else labels.add("·  " + item.getName() + "  (" + readableSize(item.length()) + ")");
        }
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("FILES · " + directory.getName())
                    .setMessage("No readable files or folders here.")
                    .setPositiveButton("CLOSE", null).show();
            return;
        }

        String title = guestPath;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    File selected = entries.get(which);
                    if (selected.isDirectory()) {
                        executeCommand("cd " + shellQuote(guestPathFor(selected)));
                    } else {
                        showFileActions(selected);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private String guestPathFor(File file) {
        try {
            String base = homeDirectory.getCanonicalPath();
            String candidate = file.getCanonicalPath();
            if (candidate.equals(base)) return GUEST_HOME;
            if (!candidate.startsWith(base + File.separator)) return GUEST_HOME;
            return GUEST_HOME + candidate.substring(base.length()).replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return GUEST_HOME;
        }
    }

    private void showFileActions(File file) {
        String[] actions = {"Preview text", "Insert cat command", "Export this file"};
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> {
            if (which == 0) previewFile(file);
            else if (which == 1) {
                commandInput.setText("cat " + shellQuote(guestPathFor(file)));
                commandInput.requestFocus();
                commandInput.setSelection(commandInput.length());
                showKeyboard();
            } else beginExportFile(file);
        }).setNegativeButton("CANCEL", null).show();
    }

    private void previewFile(File file) {
        fileExecutor.execute(() -> {
            String content;
            try (InputStream input = new FileInputStream(file);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[2048];
                int read;
                while (out.length() < MAX_PREVIEW_CHARS && (read = reader.read(buffer, 0,
                        Math.min(buffer.length, MAX_PREVIEW_CHARS - out.length()))) != -1) {
                    out.append(buffer, 0, read);
                }
                content = out.toString();
                if (file.length() > content.getBytes(StandardCharsets.UTF_8).length) content += "\n\n[Preview limited to approximately 16 KB.]";
            } catch (Exception exception) {
                content = "[Could not preview file]\n" + safeMessage(exception);
            }
            final String displayContent = content;
            runOnUiThread(() -> {
                TextView body = new TextView(this);
                body.setText(displayContent);
                body.setTextColor(TEXT);
                body.setTextSize(12);
                body.setTypeface(Typeface.MONOSPACE);
                body.setTextIsSelectable(true);
                body.setPadding(dp(12), dp(10), dp(12), dp(10));
                ScrollView scroll = new ScrollView(this);
                scroll.addView(body);
                new AlertDialog.Builder(this).setTitle(file.getName())
                        .setView(scroll).setPositiveButton("CLOSE", null).show();
            });
        });
    }

    private void beginImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }

    private void showHomeFilesForExport() {
        if (homeDirectory == null) return;
        File[] listed = homeDirectory.listFiles();
        if (listed == null) listed = new File[0];
        List<File> files = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Arrays.sort(listed, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : listed) {
            if (file.isFile() && file.canRead()) {
                files.add(file);
                labels.add(file.getName() + "  ·  " + readableSize(file.length()));
            }
        }
        if (files.isEmpty()) {
            Toast.makeText(this, "No files in the workspace yet. Create one with echo hello > hello.txt.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("EXPORT A WORKSPACE FILE")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> beginExportFile(files.get(which)))
                .setNegativeButton("CANCEL", null).show();
    }

    private void beginExportFile(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            Toast.makeText(this, "This file cannot be exported.", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingExportFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        startActivityForResult(intent, REQUEST_EXPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT_FILE) importFile(uri);
        else if (requestCode == REQUEST_EXPORT_FILE) exportFile(uri, pendingExportFile);
    }

    private void importFile(Uri uri) {
        String name = queryDisplayName(uri);
        name = new File(name).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.trim().isEmpty() || name.equals(".")) name = "imported-file";
        final String safeName = name;
        fileExecutor.execute(() -> {
            String result;
            try {
                File destination = uniqueDestination(new File(homeDirectory, safeName));
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new IllegalStateException("The selected file could not be opened.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                result = "Imported to workspace: " + destination.getName();
            } catch (Exception exception) {
                result = "Import failed: " + safeMessage(exception);
            }
            final String message = result;
            runOnUiThread(() -> {
                appendOutput("\n[PAX FILES] " + message + "\n");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                showPrompt();
            });
        });
    }

    private void exportFile(Uri uri, File source) {
        if (source == null || uri == null) return;
        fileExecutor.execute(() -> {
            String result;
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("The chosen destination could not be opened.");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                result = "Exported: " + source.getName();
            } catch (Exception exception) {
                result = "Export failed: " + safeMessage(exception);
            }
            final String message = result;
            runOnUiThread(() -> {
                appendOutput("\n[PAX FILES] " + message + "\n");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                showPrompt();
            });
        });
    }

    private String queryDisplayName(Uri uri) {
        String name = "imported-file";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return name == null ? "imported-file" : name;
    }

    private File uniqueDestination(File requested) {
        if (!requested.exists()) return requested;
        String name = requested.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; index < 10_000; index++) {
            File candidate = new File(requested.getParentFile(), stem + " (" + index + ")" + suffix);
            if (!candidate.exists()) return candidate;
        }
        return new File(requested.getParentFile(), "import-" + System.currentTimeMillis() + suffix);
    }

    private void executeCommand(String command) {
        commandInput.setText(command);
        submitCommand();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String readableSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
    }

    private void restartShell() {
        Process previous = shellProcess;
        shellAlive = false;
        try { if (shellWriter != null) shellWriter.close(); } catch (Exception ignored) { }
        shellWriter = null;
        shellProcess = null;
        if (previous != null) {
            previous.destroy();
            try { previous.destroyForcibly(); } catch (Exception ignored) { }
        }
        appendOutput("\n[Stopping the current process and restarting the shell…]\n");
        startShell();
    }

    private void updateShellBadge(String text, int color) {
        if (shellStatusBadge == null) return;
        shellStatusBadge.setText(text);
        shellStatusBadge.setTextColor(color);
    }

    private void clearTerminal() {
        if (terminalOutput != null) terminalOutput.setText("");
    }

    private String promptText() {
        String path = currentDirectory == null ? GUEST_HOME : currentDirectory;
        if (path.equals(GUEST_HOME)) path = "~";
        else if (path.startsWith(GUEST_HOME + "/")) path = "~" + path.substring(GUEST_HOME.length());
        return "pax@linux:" + path + "$ ";
    }

    private void showPrompt() {
        if (terminalOutput != null) appendOutput(promptText());
    }

    private void appendOutput(String text) {
        runOnUiThread(() -> {
            if (terminalOutput == null || isFinishing() || isDestroyed()) return;
            String safeText = text;
            if (safeText.length() > MAX_OUTPUT_CHARS) safeText = safeText.substring(safeText.length() - MAX_OUTPUT_CHARS);
            terminalOutput.append(safeText);
            CharSequence currentContent = terminalOutput.getText();
            int excess = currentContent.length() - MAX_OUTPUT_CHARS;
            if (excess > 0) {
                String currentText = currentContent.toString();
                int newline = currentText.indexOf('\n', excess);
                int cut = newline >= 0 ? newline + 1 : excess;
                terminalOutput.setText(currentText.substring(cut), TextView.BufferType.SPANNABLE);
            }
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
            commandInput.clearFocus();
        } catch (Exception ignored) { }
    }

    private void showKeyboard() {
        commandInput.post(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        shellAlive = false;
        try { if (shellWriter != null) shellWriter.close(); } catch (Exception ignored) { }
        if (shellProcess != null) shellProcess.destroy();
        fileExecutor.shutdownNow();
        super.onDestroy();
    }
}
