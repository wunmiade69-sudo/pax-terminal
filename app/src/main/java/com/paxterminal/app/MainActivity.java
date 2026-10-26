package com.paxterminal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PAX Terminal 0.2.0. This runs Android's /system/bin/sh as this app's UID.
 * It is a real, persistent local shell, but not a PTY or a full Linux userspace.
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
        subtitle.setText("LOCAL ANDROID SHELL  /  v0.2.0");
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
        footer.setText("LOCAL EXECUTION  •  ANDROID SANDBOX  •  NO ROOT / NO API KEY");
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
        try {
            if (homeDirectory == null) homeDirectory = new File(getFilesDir(), "home");
            if (!homeDirectory.exists() && !homeDirectory.mkdirs()) {
                appendOutput("Could not create the PAX Terminal home directory.\n");
                updateShellBadge("HOME ERROR", AMBER);
                return;
            }

            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh");
            builder.directory(homeDirectory);
            builder.redirectErrorStream(true);
            builder.environment().put("HOME", homeDirectory.getAbsolutePath());
            builder.environment().put("PWD", homeDirectory.getAbsolutePath());
            builder.environment().put("TERM", "dumb");
            builder.environment().put("PATH", "/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin");
            builder.environment().put("LANG", "C.UTF-8");
            Process startedProcess = builder.start();
            shellProcess = startedProcess;
            shellWriter = new BufferedWriter(new OutputStreamWriter(startedProcess.getOutputStream(), StandardCharsets.UTF_8));
            shellAlive = true;
            currentDirectory = homeDirectory.getAbsolutePath();
            lastExitCode = 0;
            updateShellBadge("SHELL ACTIVE", GREEN);

            Thread outputThread = new Thread(() -> readShellOutput(startedProcess), "pax-shell-output");
            outputThread.setDaemon(true);
            outputThread.start();

            appendOutput("PAX TERMINAL  /  v0.2.0\n");
            appendOutput("A real Android shell. Commands run locally as this app's user.\n");
            appendOutput("Type help for commands and limitations. No AI/API key required.\n\n");
            showPrompt();
        } catch (Exception exception) {
            shellAlive = false;
            updateShellBadge("SHELL ERROR", AMBER);
            appendOutput("[Could not start Android shell]\n" + safeMessage(exception) + "\n");
        }
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
            appendOutput("PAX Terminal v0.2.0 — native Android app, local shell, no AI service or API key.\n\n");
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
        appendOutput("PAX TERMINAL HELP  /  v0.2.0\n");
        appendOutput("────────────────────────────────────────────\n");
        appendOutput("Try: pwd, ls -la, cd, mkdir, cat, echo, date, id, uname\n");
        appendOutput("Examples: mkdir notes  |  cd notes  |  echo hello > hello.txt\n");
        appendOutput("PAX helpers: help, clear, history, files, pax-about\n");
        appendOutput("Use ↑ / ↓ on a hardware keyboard to browse command history.\n");
        appendOutput("FILES opens a browser for accessible folders and text previews.\n");
        appendOutput("MORE lets you import a file into HOME or export a workspace file.\n\n");
        appendOutput("HONEST LIMITS\n");
        appendOutput("• This is Android's /system/bin/sh, not a complete Linux distribution.\n");
        appendOutput("• Python, Git, apt/pkg, SSH and extra packages are not bundled yet.\n");
        appendOutput("• No PTY yet: full-screen apps, job control and Ctrl+C may not work.\n");
        appendOutput("• Commands run with this app's Android permissions; no root access.\n");
        appendOutput("• HOME is app-private; use MORE → Import/Export to transfer files.\n\n");
    }

    private void showMoreMenu() {
        String[] actions = {
                "Go to HOME directory",
                "Import a file into HOME",
                "Export a HOME file",
                "Restart shell",
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
                .setTitle("PAX Terminal v0.2.0")
                .setMessage("A free, local-first Android terminal foundation.\n\nNo AI API key or paid compute service is required. Commands execute using Android's shell and are constrained by the app sandbox. Python, Git, package management and a PTY are future upgrades.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showFileBrowser() {
        if (currentDirectory == null || currentDirectory.isEmpty()) currentDirectory = homeDirectory.getAbsolutePath();
        File directory = new File(currentDirectory);
        if (!directory.isDirectory() || !directory.canRead()) {
            Toast.makeText(this, "This folder cannot be read by PAX Terminal.", Toast.LENGTH_SHORT).show();
            return;
        }
        File[] listed = directory.listFiles();
        if (listed == null) listed = new File[0];
        Arrays.sort(listed, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        List<File> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        File parent = directory.getParentFile();
        if (parent != null && parent.canRead()) {
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

        String title = directory.getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    File selected = entries.get(which);
                    if (selected.isDirectory()) {
                        executeCommand("cd " + shellQuote(selected.getAbsolutePath()));
                    } else {
                        showFileActions(selected);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void showFileActions(File file) {
        String[] actions = {"Preview text", "Insert cat command", "Export this file"};
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> {
            if (which == 0) previewFile(file);
            else if (which == 1) {
                commandInput.setText("cat " + shellQuote(file.getAbsolutePath()));
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
            Toast.makeText(this, "No files in HOME yet. Create one with echo hello > hello.txt.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("EXPORT A HOME FILE")
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
                result = "Imported to HOME: " + destination.getName();
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
        if (previous != null) previous.destroy();
        appendOutput("\n[Restarting PAX shell…]\n");
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
        String path = currentDirectory;
        if (homeDirectory != null && path.startsWith(homeDirectory.getAbsolutePath())) {
            path = "~" + path.substring(homeDirectory.getAbsolutePath().length());
            if (path.isEmpty()) path = "~";
        }
        return "pax@android:" + path + "$ ";
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
