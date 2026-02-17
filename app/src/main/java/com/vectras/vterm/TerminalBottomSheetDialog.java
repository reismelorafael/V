package com.vectras.vterm;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.core.ProcessOutputDrainer;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vterm.view.ZoomableTextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TerminalBottomSheetDialog {
    private static final long PROCESS_TIMEOUT_MS = 5_000L;
    private static final long INTERACTIVE_PROCESS_TIMEOUT_MS = 1_500L;
    private final ZoomableTextView terminalOutput;
    private final EditText commandInput;
    private final View view;
    private final Activity activity;
    private final BottomSheetDialog bottomSheetDialog;
    LinearLayout inputContainer;
    boolean isAllowAddToResultCommand = true;

    public TerminalBottomSheetDialog(Activity activity) {
        this.activity = activity;

        bottomSheetDialog = new BottomSheetDialog(activity);
        view = activity.getLayoutInflater().inflate(R.layout.terminal_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        terminalOutput = view.findViewById(R.id.tvTerminalOutput);
        commandInput = view.findViewById(R.id.etCommandInput);
        inputContainer = view.findViewById(R.id.ln_input);

        TextView tvPrompt = view.findViewById(R.id.tvPrompt);
        updateUserPrompt(tvPrompt);

        // Show the keyboard
        forcusCommandInput();

        // Whenever you modify the text of the EditText, do the following to ensure the cursor is at the end:
        commandInput.setSelection(commandInput.getText().length());

        // when user click terminal view will open keyboard
        terminalOutput.setOnClickListener(view -> {
            forcusCommandInput();
        });
        // Configure the editor to handle the "Done" action on the soft keyboard
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                executeShellCommand(commandInput.getText().toString());
                commandInput.setText("");
                commandInput.requestFocus();
                return true;
            }
            return false;
        });

        commandInput.setOnKeyListener((v, keyCode, event) -> {
            // If the event is a key-down event on the "enter" button
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                activity.runOnUiThread(() -> appendTextAndScroll(commandInput.getText().toString() + "\n"));
                executeShellCommand(commandInput.getText().toString());
                commandInput.setText("");
                // Request focus again
                activity.runOnUiThread(commandInput::requestFocus);
                return true;
            }
            return false;
        });
    }

    public void showVterm() {
        bottomSheetDialog.show();
    }

    private void updateUserPrompt(TextView promptView) {
        // Run this in a separate thread to not block UI
        new Thread(() -> {
            String username = null;
            // Update the prompt on the UI thread
            String finalUsername = username != null ? username : "root";
            activity.runOnUiThread(() -> promptView.setText(finalUsername + "@localhost:~$ "));
        }).start();
    }

    // Function to append text and automatically scroll to bottom
    private void appendTextAndScroll(String textToAdd) {
        ScrollView scrollView = view.findViewById(R.id.scrollView);

        // Update the text
        if (textToAdd.contains("@localhost:~$ exit")) {
            bottomSheetDialog.dismiss();
        } else if (textToAdd.contains("@localhost:~$ clear")) {
            isAllowAddToResultCommand = false;
            terminalOutput.setText("");
            terminalOutput.setVisibility(View.GONE);
        } else {
            if (isAllowAddToResultCommand) {
                terminalOutput.append(textToAdd);
            } else {
                isAllowAddToResultCommand = true;
            }
        }

        // Scroll to the bottom
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && Objects.requireNonNull(inetAddress.getHostAddress()).contains(".")) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // Method to execute the shell command
    public void executeShellCommand(String userCommand) {
        if (checkInstallation())
            new Thread(() -> {
                try {
                    activity.runOnUiThread(() -> {
                        if (terminalOutput.getVisibility() == View.GONE) terminalOutput.setVisibility(View.VISIBLE);
                        appendTextAndScroll("root@localhost:~$ " + userCommand + "\n");
                        inputContainer.setVisibility(View.GONE);
                    });
                    // Setup the qemuProcess builder to start PRoot with environmental variables and commands
                    ProcessBuilder processBuilder = new ProcessBuilder();

                    // Adjust these environment variables as necessary for your app
                    String filesDir = AppConfig.internalDataDirPath;

                    ProotCommandBuilder prootCommandBuilder = new ProotCommandBuilder(activity, filesDir + "/distro", "/root")
                            .setFilesDirPath(filesDir)
                            .setDisplay(":0")
                            .setPulseServer("127.0.0.1")
                            .setXdgRuntimeDir("${TMPDIR}")
                            .setSdlVideoDriver("x11");
                    prootCommandBuilder.applyEnvironment(processBuilder.environment());
                    processBuilder.command(prootCommandBuilder.buildCommand());
                    Process process = null;
                    ProcessOutputDrainer drainer = new ProcessOutputDrainer();
                    BufferedWriter writer = null;
                    ExecutorService drainExecutor = Executors.newSingleThreadExecutor();
                    Future<?> drainFuture = null;
                    try {
                        process = processBuilder.start();
                        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

                        // Send user command to PRoot
                        writer.write(userCommand);
                        writer.newLine();
                        writer.flush();
                        writer.close();
                        writer = null;

                        Process runningProcess = process;
                        drainFuture = drainExecutor.submit(() -> {
                            try {
                                drainer.drain(runningProcess, (stream, line) -> activity.runOnUiThread(() ->
                                appendTextAndScroll("[" + stream + "] " + line + "\n")));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

                        long timeoutMs = isLikelyInteractiveCommand(userCommand)
                                ? INTERACTIVE_PROCESS_TIMEOUT_MS
                                : PROCESS_TIMEOUT_MS;
                        if (!runningProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                            runningProcess.destroy();
                            if (!runningProcess.waitFor(300, TimeUnit.MILLISECONDS)) {
                                runningProcess.destroyForcibly();
                            }
                            activity.runOnUiThread(() -> appendTextAndScroll(
                                    "[stderr] timeout after " + timeoutMs + "ms\n"));
                        }
                        if (drainFuture != null) {
                            try {
                                drainFuture.get(750, TimeUnit.MILLISECONDS);
                            } catch (Exception ignored) {
                                drainFuture.cancel(true);
                            }
                        }
                    } finally {
                        if (writer != null) {
                            try {
                                writer.close();
                            } catch (IOException ignored) {
                                // best effort
                            }
                        }
                        if (drainFuture != null) {
                            drainFuture.cancel(true);
                        }
                        drainExecutor.shutdownNow();
                        drainer.shutdown();
                        if (process != null) {
                            process.destroy();
                        }
                        activity.runOnUiThread(() -> {
                            inputContainer.setVisibility(View.VISIBLE);
                            forcusCommandInput();
                        });
                    }

                } catch (IOException | InterruptedException e) {
                    // Handle exceptions by printing the stack trace in the terminal output
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    final String errorMessage = e.getMessage();
                    activity.runOnUiThread(() -> {
                        appendTextAndScroll("Error: " + errorMessage + "n");
                        inputContainer.setVisibility(View.VISIBLE);
                        forcusCommandInput();
                    });
                }
            }).start(); // Execute the command in a separate thread to prevent blocking the UI thread
        else
            new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                    .setTitle("Error!")
                    .setMessage("Verify that \"setupFiles()\" is working properly in onCreate().")
                    .setCancelable(false)
                    .show();
    }

    private boolean checkInstallation() {
        String filesDir = activity.getFilesDir().getAbsolutePath();
        File distro = new File(filesDir, "distro");
        return distro.exists();
    }

    private boolean isLikelyInteractiveCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase();
        return normalized.isEmpty()
                || "bash".equals(normalized)
                || "sh".equals(normalized)
                || normalized.startsWith("top")
                || normalized.startsWith("vi")
                || normalized.startsWith("vim")
                || normalized.startsWith("less")
                || normalized.startsWith("more");
    }

    private void forcusCommandInput() {
        commandInput.post(() -> {
            commandInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }
}
