package com.paxterminal.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * PAX Terminal 0.1.0: a minimal, real Android system-shell front end.
 * Commands run as this app's Android UID and are subject to Android's sandbox.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 17, 14);
    private static final int PANEL = Color.rgb(13, 27, 22);
    private static final int PANEL_2 = Color.rgb(18, 35, 28);
    private static final int GREEN = Color.rgb(184, 243, 107);
    private static final int TEXT = Color.rgb(224, 239, 226);
    private static final int MUTED = Color.rgb(135, 164, 143);
    private static final String META_PREFIX = "__PAX_META__";

    private LinearLayout root;
    private TextView terminalOutput;
    private ScrollView outputScroll;
    private EditText commandInput;
    private BufferedWriter shellWriter;
    private Process shellProcess;
    private volatile boolean shellAlive = false;
    private volatile String currentDirectory = "/";
    private File homeDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildInterface();
        startShell();
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(10));

        LinearLayout brandStack = new LinearLayout(this);
        brandStack.setOrientation(LinearLayout.VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("PAX TERMINAL");
        brand.setTextColor(GREEN);
        brand.setTextSize(19);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID SHELL  /  v0.1.0");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(10);
        subtitle.setLetterSpacing(0.08f);
        brandStack.addView(brand);
        brandStack.addView(subtitle);
        header.addView(brandStack, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView status = new TextView(this);
        status.setText("● LOCAL · NO API KEY");
        status.setTextColor(GREEN);
        status.setTextSize(9);
        status.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        status.setPadding(dp(8), dp(7), dp(8), dp(7));
        status.setBackground(roundRect(PANEL_2, dp(10)));
        header.addView(status);
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
        outputParams.topMargin = dp(10);
        outputParams.bottomMargin = dp(10);
        root.addView(outputScroll, outputParams);

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        quickActions.setGravity(Gravity.CENTER_VERTICAL);
        quickActions.addView(makeActionButton("HELP", v -> showHelp()), new LinearLayout.LayoutParams(0, dp(38), 1f));
        quickActions.addView(makeActionButton("CLEAR", v -> clearTerminal()), spacedWeight());
        quickActions.addView(makeActionButton("HOME", v -> insertText("cd \"$HOME\"")), spacedWeight());
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
        commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        commandInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitCommand();
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
        footer.setText("REAL DEVICE SHELL  •  SANDBOXED BY ANDROID  •  PYTHON/GIT NOT BUNDLED YET");
        footer.setTextColor(MUTED);
        footer.setTextSize(8);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(2), dp(9), dp(2), dp(2));
        root.addView(footer);

        setContentView(root);
    }

    private LinearLayout.LayoutParams spacedWeight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), 1f);
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
            homeDirectory = new File(getFilesDir(), "home");
            if (!homeDirectory.exists() && !homeDirectory.mkdirs()) {
                appendOutput("Could not create app home directory.\n");
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
            shellProcess = builder.start();
            shellWriter = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream(), StandardCharsets.UTF_8));
            shellAlive = true;
            currentDirectory = homeDirectory.getAbsolutePath();

            Thread outputThread = new Thread(() -> readShellOutput(shellProcess), "pax-shell-output");
            outputThread.setDaemon(true);
            outputThread.start();

            appendOutput("PAX TERMINAL  /  v0.1.0\n");
            appendOutput("A lightweight Android shell. Commands execute locally.\n");
            appendOutput("No AI API key required. Type help to get started.\n\n");
            showPrompt();
        } catch (Exception exception) {
            shellAlive = false;
            appendOutput("[Could not start Android shell]\n" + exception.getMessage() + "\n");
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
                        currentDirectory = newPath;
                        runOnUiThread(this::showPrompt);
                    }
                } else {
                    appendOutput(line + "\n");
                }
            }
        } catch (Exception exception) {
            // The shell may be closed normally by an exit command.
        } finally {
            shellAlive = false;
            runOnUiThread(() -> {
                appendOutput("\n[Shell stopped. Submit another command to start a new shell.]\n");
                showPrompt();
            });
        }
    }

    private void submitCommand() {
        if (commandInput == null) return;
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) return;
        commandInput.setText("");
        hideKeyboard();
        appendOutput(promptText() + command + "\n");

        if (command.equals("help") || command.equals("pax-help")) {
            showHelp();
            showPrompt();
            return;
        }
        if (command.equals("clear") || command.equals("cls")) {
            clearTerminal();
            showPrompt();
            return;
        }
        if (!shellAlive || shellProcess == null || !shellProcess.isAlive()) {
            startShell();
        }
        try {
            if (shellWriter == null) {
                appendOutput("[Shell is unavailable. Close and reopen PAX Terminal.]\n");
                return;
            }
            shellWriter.write(command);
            shellWriter.newLine();
            shellWriter.write("__pax_status=$?; printf '\\n__PAX_META__%s|%s\\n' \"$PWD\" \"$__pax_status\"");
            shellWriter.newLine();
            shellWriter.flush();
        } catch (Exception exception) {
            appendOutput("[Could not send command] " + exception.getMessage() + "\n");
        }
    }

    private void showHelp() {
        appendOutput("PAX TERMINAL HELP\n");
        appendOutput("────────────────────────────────────────\n");
        appendOutput("Try: pwd, ls, cd, mkdir, cat, echo, date, id, uname\n");
        appendOutput("Examples: ls -l   |   mkdir notes   |   cd notes\n\n");
        appendOutput("This version runs Android's /system/bin/sh. Available commands\n");
        appendOutput("depend on the Android device. It does not include a Linux\n");
        appendOutput("package manager, Python, Git, SSH, or root access yet.\n");
        appendOutput("Use Android's app-private home directory for files for now.\n\n");
    }

    private void clearTerminal() {
        terminalOutput.setText("");
    }

    private void insertText(String text) {
        commandInput.requestFocus();
        commandInput.setText(text);
        commandInput.setSelection(commandInput.length());
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
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
        if (terminalOutput == null) return;
        // The prompt is visual; commands themselves still execute in the persistent shell process.
        appendOutput(promptText());
    }

    private void appendOutput(String text) {
        runOnUiThread(() -> {
            if (terminalOutput == null) return;
            terminalOutput.append(text);
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
            commandInput.clearFocus();
        } catch (Exception ignored) {
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        shellAlive = false;
        try {
            if (shellWriter != null) shellWriter.close();
        } catch (Exception ignored) {
        }
        if (shellProcess != null) shellProcess.destroy();
        super.onDestroy();
    }
}
