package ropl.momo.item;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.topjohnwu.superuser.ipc.RootService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ropl.momo.item.IPCCond.AIDLConnection;
import ropl.momo.item.IPCCond.IPCService;
import ropl.momo.item.IPCCond.SuperMain;
import ropl.momo.item.Service.FloatService;
import ropl.momo.item.ToolClass.FloatTool;

public class MainActivity extends Activity {

    private EditText inputKey;
    private Button siButton, btnLogin;
    private TextView tvStatus, btnPaste;
    private SharedPreferences prefs;
    private boolean isStreamModeEnabled = false;
    private AtomicBoolean isServiceConnected = new AtomicBoolean(false);
    private LinearLayout mainContainer;
    private View.OnClickListener currentLoginListener;
    private TextView tvRunningStatus;

    private String userLicenseKey = "";
    private long expiryTimestamp = 0;
    private Handler countdownHandler = new Handler();
    private Runnable countdownRunnable;
    private TextView tvCountdown;
    // Dark theme colors
    private static final String COLOR_BG_DARK = "#0D0D0D";
    private static final String COLOR_CARD_DARK = "#1A1A1A";
    private static final String COLOR_CARD_BORDER = "#FF6B00";
    private static final String COLOR_TEXT_PRIMARY = "#FFFFFF";
    private static final String COLOR_TEXT_SECONDARY = "#B0B0B0";
    private static final String COLOR_TEXT_HINT = "#666666";
    private static final String COLOR_INPUT_BG = "#2A2A2A";
    private static final String COLOR_INPUT_BORDER = "#3A3A3A";
    private static final String COLOR_ACCENT = "#FF6B00";
    private static final String COLOR_SUCCESS = "#00E676";
    private static final String COLOR_ERROR = "#FF5252";
    private static final String COLOR_DIVIDER = "#2A2A2A";
    private static final String COLOR_TELEGRAM = "#0088CC";
    private static final String COLOR_RED = "#E53935";

    // dp() method - CLASS LEVEL, Java 7 compatible
    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start the app
        proceedToNormalApp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ============================================
    private void proceedToNormalApp() {
        // Create UI programmatically - DARK THEME VIP LOOK
        createUI();

        prefs = getSharedPreferences("user_info", MODE_PRIVATE);
        String savedKey = prefs.getString("password", "");

        // Load saved key if exists
        if (!savedKey.isEmpty() && inputKey != null) {
            inputKey.setText(savedKey);
        }

        // Check Root Permission
        if (!FloatTool.isRoot()) {
            Toast.makeText(this, "Device requires ROOT permission!", Toast.LENGTH_LONG).show();
        }

        // Android 12 specific settings
        if (Build.VERSION.SDK_INT <= 31) {
            FloatTool.RunShell("settings put global block_untrusted_touches 0", true);
        }

        // Grant overlay permission
        for (int i = 0; i < 5; i++) {
            if (!Settings.canDrawOverlays(this)) {
                FloatTool.RunShell("appops set --uid " + getPackageName() + " android:system_alert_window allow", true);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                                       Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 4444);
        }

        // Connect IPC service via RootService
        RootService.bind(
            new Intent(this, SuperMain.class),
            new AIDLConnection(false) {
                @Override
                public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                    super.onServiceConnected(name, service);
                    isServiceConnected.set(true);
                    updateLoginButtonState();
                    updateStatusText();
                }

                @Override
                public void onServiceDisconnected(android.content.ComponentName name) {
                    super.onServiceDisconnected(name);
                    isServiceConnected.set(false);
                    updateLoginButtonState();
                    updateStatusText();
                }
            }
        );

        // Initialize login listener
        currentLoginListener = createLoginClickListener();
    }

    private void updateStatusText() {
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tvStatus != null) {
                        if (isServiceConnected.get()) {
                            tvStatus.setText("SERVICE CONNECTED");
                            tvStatus.setTextColor(Color.parseColor(COLOR_SUCCESS));
                            tvStatus.setShadowLayer(8, 0, 0, Color.parseColor(COLOR_SUCCESS));
                        } else {
                            tvStatus.setText("CONNECTING TO SERVICE...");
                            tvStatus.setTextColor(Color.parseColor(COLOR_ACCENT));
                            tvStatus.setShadowLayer(8, 0, 0, Color.parseColor(COLOR_ACCENT));
                        }
                    }
                }
            });
    }

    private void updateLoginButtonState() {
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (btnLogin != null && inputKey != null) {
                        boolean hasText = inputKey.getText().toString().trim().length() > 0;
                        boolean enabled = isServiceConnected.get() && hasText;
                        btnLogin.setEnabled(enabled);
                        btnLogin.setAlpha(enabled ? 1.0f : 0.5f);

                        if (isServiceConnected.get()) {
                            btnLogin.setText("LOGIN");
                            if (hasText) {
                                btnLogin.setOnClickListener(currentLoginListener);
                            } else {
                                btnLogin.setOnClickListener(null);
                            }
                        } else {
                            btnLogin.setText("CONNECTING...");
                            btnLogin.setOnClickListener(null);
                        }
                    }
                }
            });
    }

    private View.OnClickListener createLoginClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isServiceConnected.get()) {
                    Toast.makeText(MainActivity.this, "SERVICE NOT READY!", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String userKey = inputKey.getText().toString().trim();

                if (userKey.isEmpty()) {
                    Toast.makeText(MainActivity.this, "ENTER LICENSE KEY!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Save key
                prefs.edit().putString("password", userKey).apply();

                // Show verification dialog - DARK THEME
                showVerificationDialog(userKey);
            }
        };
    }

    private void showVerificationDialog(final String userKey) {
        LinearLayout verifyLayout = new LinearLayout(MainActivity.this);
        verifyLayout.setOrientation(LinearLayout.VERTICAL);
        verifyLayout.setGravity(Gravity.CENTER);
        verifyLayout.setPadding(dp(25), dp(25), dp(25), dp(25));
        verifyLayout.setBackgroundColor(Color.parseColor(COLOR_CARD_DARK));

        GradientDrawable verifyBg = new GradientDrawable();
        verifyBg.setColor(Color.parseColor(COLOR_CARD_DARK));
        verifyBg.setCornerRadius(dp(20));
        verifyBg.setStroke(2, Color.parseColor(COLOR_ACCENT));
        verifyLayout.setBackground(verifyBg);

        final TextView verifyIcon = new TextView(MainActivity.this);
        verifyIcon.setText("O");
        verifyIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        verifyIcon.setGravity(Gravity.CENTER);
        verifyIcon.setTextColor(Color.parseColor(COLOR_ACCENT));
        verifyIcon.setShadowLayer(15, 0, 0, Color.parseColor(COLOR_ACCENT));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = dp(15);
        verifyIcon.setLayoutParams(iconParams);
        verifyLayout.addView(verifyIcon);

        TextView verifyTitle = new TextView(MainActivity.this);
        verifyTitle.setText("VERIFYING LICENSE");
        verifyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        verifyTitle.setTypeface(null, Typeface.BOLD);
        verifyTitle.setGravity(Gravity.CENTER);
        verifyTitle.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(8);
        verifyTitle.setLayoutParams(titleParams);
        verifyLayout.addView(verifyTitle);

        TextView verifySubtitle = new TextView(MainActivity.this);
        verifySubtitle.setText("Checking: " + maskKey(userKey));
        verifySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        verifySubtitle.setGravity(Gravity.CENTER);
        verifySubtitle.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        verifySubtitle.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(20);
        verifySubtitle.setLayoutParams(subParams);
        verifyLayout.addView(verifySubtitle);

        LinearLayout stepsContainer = new LinearLayout(MainActivity.this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsContainer.setPadding(dp(10), 0, dp(10), 0);

        final TextView step1 = createStepText("Connecting to server...", false);
        final TextView step2 = createStepText("Validating license key...", false);
        final TextView step3 = createStepText("Fetching user data...", false);

        stepsContainer.addView(step1);
        stepsContainer.addView(step2);
        stepsContainer.addView(step3);
        verifyLayout.addView(stepsContainer);

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_ACCENT)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        progressParams.topMargin = dp(20);
        progressBar.setLayoutParams(progressParams);
        verifyLayout.addView(progressBar);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(verifyLayout);
        builder.setCancelable(false);
        final AlertDialog verifyDialog = builder.create();

        if (verifyDialog.getWindow() != null) {
            verifyDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        verifyDialog.show();

        final android.os.Handler iconHandler = new android.os.Handler();
        final Runnable iconRunnable = new Runnable() {
            int frame = 0;
            String[] frames = new String[]{"O", "o", ".", "o", "O"};

            @Override
            public void run() {
                if (verifyDialog.isShowing()) {
                    frame = (frame + 1) % frames.length;
                    verifyIcon.setText(frames[frame]);
                    iconHandler.postDelayed(this, 200);
                }
            }
        };
        iconHandler.post(iconRunnable);

        new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(800);
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStepText(step1, true);
                                }
                            });

                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStepText(step2, true);
                                }
                            });

                        int retryCount = 0;
                        while (IPCService.GetIPC() == null && retryCount < 10) {
                            Thread.sleep(500);
                            retryCount++;
                        }

                        if (IPCService.GetIPC() == null) {
                            throw new RemoteException("Cannot connect to service");
                        }

                        // Device ID generation (local only)
                        String deviceId = getMediaDrmId();
                        if (deviceId == null || deviceId.isEmpty()) {
                            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                        }
                        if (deviceId == null || deviceId.isEmpty()) {
                            deviceId = "UnknownDevice";
                        }
                        try {
                            IPCService.GetIPC().SetKamiImei("any_string", deviceId);
                        } catch (android.os.RemoteException e) {
                            e.printStackTrace();
                        }
                        final String result = IPCService.GetIPC().login(userKey);

                        // Parse expiry from result (expected format: "OK|timestamp")
                        final String[] resultParts = result.split("\\|");
                        if (resultParts.length >= 2) {
                            try {
                                expiryTimestamp = Long.parseLong(resultParts[1]);
                            } catch (NumberFormatException e) {
                                expiryTimestamp = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000); // Default 7 days
                            }
                        } else {
                            expiryTimestamp = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000); // Default 7 days
                        }

                        userLicenseKey = userKey;

                        Thread.sleep(500);
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStepText(step3, true);
                                }
                            });

                        Thread.sleep(500);

                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    iconHandler.removeCallbacks(iconRunnable);
                                    verifyDialog.dismiss();
                                    if (result != null && result.startsWith("OK")) {
                                        showSuccessDialog();
                                    } else {
                                        showVIPErrorDialog(result != null ? result : "INVALID LICENSE KEY");
                                    }
                                }
                            });

                    } catch (final RemoteException e) {
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    iconHandler.removeCallbacks(iconRunnable);
                                    verifyDialog.dismiss();
                                    showVIPErrorDialog("CONNECTION FAILED!");
                                }
                            });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
    }

    private TextView createStepText(String text, boolean completed) {
        TextView step = new TextView(MainActivity.this);
        step.setText((completed ? "[OK] " : "[  ] ") + text);
        step.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        step.setTextColor(Color.parseColor(completed ? COLOR_SUCCESS : COLOR_TEXT_SECONDARY));
        step.setTypeface(Typeface.MONOSPACE);
        step.setPadding(0, dp(4), 0, dp(4));
        return step;
    }

    private void updateStepText(TextView step, boolean completed) {
        String text = step.getText().toString();
        if (completed) {
            step.setText(text.replace("[  ]", "[OK]"));
            step.setTextColor(Color.parseColor(COLOR_SUCCESS));
            step.setShadowLayer(4, 0, 0, Color.parseColor(COLOR_SUCCESS));
        }
    }

    private String maskKey(String key) {
        if (key.length() <= 8) {
            return "****" + key.substring(key.length() - 4);
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private void showSuccessDialog() {
        LinearLayout successLayout = new LinearLayout(MainActivity.this);
        successLayout.setOrientation(LinearLayout.VERTICAL);
        successLayout.setGravity(Gravity.CENTER);
        successLayout.setPadding(dp(30), dp(30), dp(30), dp(30));

        GradientDrawable successBg = new GradientDrawable();
        successBg.setColor(Color.parseColor(COLOR_CARD_DARK));
        successBg.setCornerRadius(dp(20));
        successBg.setStroke(2, Color.parseColor(COLOR_SUCCESS));
        successLayout.setBackground(successBg);

        TextView successIcon = new TextView(MainActivity.this);
        successIcon.setText("OK");
        successIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
        successIcon.setGravity(Gravity.CENTER);
        successIcon.setTextColor(Color.parseColor(COLOR_SUCCESS));
        successIcon.setShadowLayer(15, 0, 0, Color.parseColor(COLOR_SUCCESS));
        successIcon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = dp(15);
        successIcon.setLayoutParams(iconParams);
        successLayout.addView(successIcon);

        TextView successTitle = new TextView(MainActivity.this);
        successTitle.setText("WELCOME BABY");
        successTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        successTitle.setTypeface(null, Typeface.BOLD);
        successTitle.setGravity(Gravity.CENTER);
        successTitle.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(5);
        successTitle.setLayoutParams(titleParams);
        successLayout.addView(successTitle);

        TextView successSubtitle = new TextView(MainActivity.this);
        successSubtitle.setText("LICENSE VERIFIED SUCCESSFULLY");
        successSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        successSubtitle.setGravity(Gravity.CENTER);
        successSubtitle.setTextColor(Color.parseColor(COLOR_SUCCESS));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(20);
        successSubtitle.setLayoutParams(subParams);
        successLayout.addView(successSubtitle);

        Button continueBtn = new Button(MainActivity.this);
        continueBtn.setText("CONTINUE");
        continueBtn.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        continueBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        continueBtn.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(COLOR_SUCCESS));
        btnBg.setCornerRadius(dp(12));
        continueBtn.setBackground(btnBg);
        continueBtn.setPadding(dp(30), dp(12), dp(30), dp(12));

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(successLayout);
        builder.setCancelable(false);
        final AlertDialog successDialog = builder.create();

        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        continueBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    successDialog.dismiss();
                    loadMainUI();
                }
            });

        successLayout.addView(continueBtn);
        successDialog.show();
    }

    private void showVIPErrorDialog(String message) {
        LinearLayout errorLayout = new LinearLayout(MainActivity.this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setPadding(dp(30), dp(30), dp(30), dp(30));

        GradientDrawable errorBg = new GradientDrawable();
        errorBg.setColor(Color.parseColor(COLOR_CARD_DARK));
        errorBg.setCornerRadius(dp(20));
        errorBg.setStroke(2, Color.parseColor(COLOR_ERROR));
        errorLayout.setBackground(errorBg);

        TextView errorIcon = new TextView(MainActivity.this);
        errorIcon.setText("X");
        errorIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
        errorIcon.setGravity(Gravity.CENTER);
        errorIcon.setTextColor(Color.parseColor(COLOR_ERROR));
        errorIcon.setShadowLayer(15, 0, 0, Color.parseColor(COLOR_ERROR));
        errorIcon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = dp(15);
        errorIcon.setLayoutParams(iconParams);
        errorLayout.addView(errorIcon);

        TextView errorTitle = new TextView(MainActivity.this);
        errorTitle.setText("LOGIN FAILED");
        errorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        errorTitle.setTypeface(null, Typeface.BOLD);
        errorTitle.setTextColor(Color.parseColor(COLOR_ERROR));
        errorTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(10);
        errorTitle.setLayoutParams(titleParams);
        errorLayout.addView(errorTitle);

        TextView errorMsg = new TextView(MainActivity.this);
        errorMsg.setText(message);
        errorMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        errorMsg.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        errorMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.bottomMargin = dp(20);
        errorMsg.setLayoutParams(msgParams);
        errorLayout.addView(errorMsg);

        Button closeBtn = new Button(MainActivity.this);
        closeBtn.setText("TRY AGAIN");
        closeBtn.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(COLOR_ACCENT));
        btnBg.setCornerRadius(dp(12));
        closeBtn.setBackground(btnBg);
        closeBtn.setPadding(dp(20), dp(12), dp(20), dp(12));

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(errorLayout);
        builder.setCancelable(true);
        final AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

        errorLayout.addView(closeBtn);
        dialog.show();
    }

    // ============================================
    // EXPIRED KEY DIALOG
    // ============================================
    private void showKeyExpiredDialog() {
        LinearLayout expiredLayout = new LinearLayout(MainActivity.this);
        expiredLayout.setOrientation(LinearLayout.VERTICAL);
        expiredLayout.setGravity(Gravity.CENTER);
        expiredLayout.setPadding(dp(30), dp(30), dp(30), dp(30));

        GradientDrawable expiredBg = new GradientDrawable();
        expiredBg.setColor(Color.parseColor(COLOR_CARD_DARK));
        expiredBg.setCornerRadius(dp(20));
        expiredBg.setStroke(2, Color.parseColor(COLOR_ERROR));
        expiredLayout.setBackground(expiredBg);

        TextView expiredIcon = new TextView(MainActivity.this);
        expiredIcon.setText("!");
        expiredIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
        expiredIcon.setGravity(Gravity.CENTER);
        expiredIcon.setTextColor(Color.parseColor(COLOR_ERROR));
        expiredIcon.setShadowLayer(15, 0, 0, Color.parseColor(COLOR_ERROR));
        expiredIcon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = dp(15);
        expiredIcon.setLayoutParams(iconParams);
        expiredLayout.addView(expiredIcon);

        TextView expiredTitle = new TextView(MainActivity.this);
        expiredTitle.setText("KEY EXPIRED");
        expiredTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        expiredTitle.setTypeface(null, Typeface.BOLD);
        expiredTitle.setTextColor(Color.parseColor(COLOR_ERROR));
        expiredTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(10);
        expiredTitle.setLayoutParams(titleParams);
        expiredLayout.addView(expiredTitle);

        TextView expiredMsg = new TextView(MainActivity.this);
        expiredMsg.setText("Your license key has expired.\nPlease contact admin for renewal.");
        expiredMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        expiredMsg.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        expiredMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.bottomMargin = dp(20);
        expiredMsg.setLayoutParams(msgParams);
        expiredLayout.addView(expiredMsg);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        String expiryDate = sdf.format(new Date(expiryTimestamp));

        TextView expiredDate = new TextView(MainActivity.this);
        expiredDate.setText("Expired on: " + expiryDate);
        expiredDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        expiredDate.setTextColor(Color.parseColor(COLOR_ACCENT));
        expiredDate.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.bottomMargin = dp(20);
        expiredDate.setLayoutParams(dateParams);
        expiredLayout.addView(expiredDate);

        Button closeBtn = new Button(MainActivity.this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setTypeface(null, Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor(COLOR_ACCENT));
        closeBg.setCornerRadius(dp(12));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(dp(20), dp(12), dp(20), dp(12));

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(expiredLayout);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    // Exit the app when key is expired
                    finishAffinity();
                }
            });

        expiredLayout.addView(closeBtn);
        dialog.show();
    }

    private void createUI() {
        mainContainer = new LinearLayout(this);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                          LinearLayout.LayoutParams.MATCH_PARENT,
                                          LinearLayout.LayoutParams.MATCH_PARENT
                                      ));
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setGravity(Gravity.CENTER);
        mainContainer.setBackgroundColor(Color.parseColor(COLOR_BG_DARK));
        mainContainer.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout cardContainer = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            dp(350),
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardContainer.setLayoutParams(cardParams);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setGravity(Gravity.CENTER);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(COLOR_CARD_DARK));
        cardBg.setCornerRadius(dp(25));
        cardBg.setStroke(2, Color.parseColor(COLOR_ACCENT));
        cardContainer.setBackground(cardBg);
        cardContainer.setPadding(dp(30), dp(35), dp(30), dp(35));

        TextView vipBadge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        badgeParams.bottomMargin = dp(10);
        vipBadge.setLayoutParams(badgeParams);
        vipBadge.setText("VIP");
        vipBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        vipBadge.setTypeface(null, Typeface.BOLD);
        vipBadge.setGravity(Gravity.CENTER);
        vipBadge.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        vipBadge.setShadowLayer(8, 0, 0, Color.parseColor(COLOR_ACCENT));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor(COLOR_ACCENT));
        badgeBg.setCornerRadius(dp(15));
        vipBadge.setBackground(badgeBg);
        vipBadge.setPadding(dp(15), dp(5), dp(15), dp(5));
        cardContainer.addView(vipBadge);

        TextView logoIcon = new TextView(this);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logoParams.bottomMargin = dp(10);
        logoIcon.setLayoutParams(logoParams);
        logoIcon.setText("STREAM Ex");
        logoIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
        logoIcon.setGravity(Gravity.CENTER);
        logoIcon.setTextColor(Color.parseColor(COLOR_ACCENT));
        logoIcon.setShadowLayer(20, 0, 0, Color.parseColor(COLOR_ACCENT));
        logoIcon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cardContainer.addView(logoIcon);

        TextView appTitle = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dp(5);
        appTitle.setLayoutParams(titleParams);
        appTitle.setText("FREE FIRE MAX");
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        cardContainer.addView(appTitle);

        TextView subtitle = new TextView(this);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subParams.bottomMargin = dp(20);
        subtitle.setLayoutParams(subParams);
        subtitle.setText("VERSION");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Color.parseColor(COLOR_ACCENT));
        subtitle.setTypeface(null, Typeface.BOLD);
        subtitle.setShadowLayer(8, 0, 0, Color.parseColor(COLOR_ACCENT));
        cardContainer.addView(subtitle);

        tvStatus = new TextView(this);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.bottomMargin = dp(20);
        tvStatus.setLayoutParams(statusParams);
        tvStatus.setText("CONNECTING TO SERVICE...");
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextColor(Color.parseColor(COLOR_ACCENT));
        tvStatus.setShadowLayer(8, 0, 0, Color.parseColor(COLOR_ACCENT));
        cardContainer.addView(tvStatus);

        LinearLayout inputContainer = new LinearLayout(this);
        LinearLayout.LayoutParams inputContainerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(55)
        );
        inputContainerParams.bottomMargin = dp(20);
        inputContainer.setLayoutParams(inputContainerParams);
        inputContainer.setOrientation(LinearLayout.HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor(COLOR_INPUT_BG));
        inputBg.setCornerRadius(dp(15));
        inputBg.setStroke(1, Color.parseColor(COLOR_INPUT_BORDER));
        inputContainer.setBackground(inputBg);
        inputContainer.setPadding(dp(15), 0, dp(10), 0);

        inputKey = new EditText(this);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        inputKey.setLayoutParams(keyParams);
        inputKey.setHint("ENTER LICENSE KEY");
        inputKey.setHintTextColor(Color.parseColor(COLOR_TEXT_HINT));
        inputKey.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        inputKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        inputKey.setBackground(null);
        inputKey.setSingleLine();
        inputKey.setTypeface(Typeface.MONOSPACE);
        inputContainer.addView(inputKey);

        btnPaste = new TextView(this);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(
            dp(70),
            dp(38)
        );
        btnPaste.setLayoutParams(pasteParams);
        btnPaste.setText("PASTE");
        btnPaste.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnPaste.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        btnPaste.setTypeface(null, Typeface.BOLD);
        btnPaste.setGravity(Gravity.CENTER);

        GradientDrawable pasteBg = new GradientDrawable();
        pasteBg.setColor(Color.parseColor(COLOR_SUCCESS));
        pasteBg.setCornerRadius(dp(10));
        btnPaste.setBackground(pasteBg);

        btnPaste.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard.hasPrimaryClip()) {
                        ClipData clipData = clipboard.getPrimaryClip();
                        if (clipData != null && clipData.getItemCount() > 0) {
                            String pastedText = clipData.getItemAt(0).getText().toString();
                            if (pastedText != null && !pastedText.isEmpty()) {
                                inputKey.setText(pastedText);
                                Toast.makeText(MainActivity.this, "KEY PASTED!", Toast.LENGTH_SHORT).show();
                                updateLoginButtonState();
                            }
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "CLIPBOARD EMPTY!", Toast.LENGTH_SHORT).show();
                    }
                }
            });

        inputContainer.addView(btnPaste);
        cardContainer.addView(inputContainer);

        btnLogin = new Button(this);
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(55)
        );
        loginParams.bottomMargin = dp(20);
        btnLogin.setLayoutParams(loginParams);
        btnLogin.setText("LOGIN");
        btnLogin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnLogin.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        btnLogin.setTypeface(null, Typeface.BOLD);

        GradientDrawable loginBg = new GradientDrawable();
        loginBg.setColor(Color.parseColor(COLOR_ACCENT));
        loginBg.setCornerRadius(dp(15));
        btnLogin.setBackground(loginBg);

        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.5f);

        inputKey.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean hasText = s != null && s.toString().trim().length() > 0;
                    boolean enabled = hasText && isServiceConnected.get();
                    btnLogin.setEnabled(enabled);
                    btnLogin.setAlpha(enabled ? 1.0f : 0.5f);
                    if (enabled) {
                        btnLogin.setOnClickListener(currentLoginListener);
                    } else {
                        btnLogin.setOnClickListener(null);
                    }
                }
                public void afterTextChanged(Editable s) {}
            });

        cardContainer.addView(btnLogin);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        );
        dividerParams.bottomMargin = dp(20);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(Color.parseColor(COLOR_DIVIDER));
        cardContainer.addView(divider);

        TextView footer = new TextView(this);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        footer.setLayoutParams(footerParams);
        footer.setText("ROOT ONLY | SECURE & SAFE");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(Color.parseColor(COLOR_TEXT_HINT));
        cardContainer.addView(footer);

        mainContainer.addView(cardContainer);
        setContentView(mainContainer);
    }

    private Drawable createVIPBadgeBackground() {
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor(COLOR_ACCENT));
        badgeBg.setCornerRadius(dp(15));
        return badgeBg;
    }

    // ============================================
    // LOAD MAIN UI - KEY INFO ONLY (NO EXPIRY/TIME)
    // ============================================
    private void loadMainUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                       LinearLayout.LayoutParams.MATCH_PARENT,
                                       LinearLayout.LayoutParams.MATCH_PARENT
                                   ));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mainLayout.setBackgroundColor(Color.parseColor(COLOR_BG_DARK));
        mainLayout.setPadding(dp(20), dp(40), dp(20), dp(20));

        // --- TOP STATUS CARD: LICENSE ACTIVE ---
        LinearLayout statusCard = new LinearLayout(this);
        LinearLayout.LayoutParams statusCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusCardParams.setMargins(dp(0), dp(0), dp(0), dp(20));
        statusCard.setLayoutParams(statusCardParams);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding(dp(20), dp(18), dp(20), dp(18));

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.parseColor("#1A1A1A"));
        statusBg.setCornerRadius(dp(18));
        statusCard.setBackground(statusBg);

        View greenDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMargins(0, 0, dp(12), 0);
        greenDot.setLayoutParams(dotParams);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(Color.parseColor(COLOR_SUCCESS));
        dotBg.setShape(GradientDrawable.OVAL);
        greenDot.setBackground(dotBg);
        statusCard.addView(greenDot);

        LinearLayout statusTextContainer = new LinearLayout(this);
        statusTextContainer.setOrientation(LinearLayout.VERTICAL);
        statusTextContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                            ));

        TextView tvLicenseActive = new TextView(this);
        tvLicenseActive.setText("LICENSE ACTIVE");
        tvLicenseActive.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvLicenseActive.setTypeface(null, Typeface.BOLD);
        tvLicenseActive.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        statusTextContainer.addView(tvLicenseActive);

        TextView tvVerified = new TextView(this);
        tvVerified.setText("Verified · STREAM Ex");
        tvVerified.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvVerified.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        statusTextContainer.addView(tvVerified);

        statusCard.addView(statusTextContainer);
        mainLayout.addView(statusCard);

        // --- MAIN CARD: NIKU MODS ---
        LinearLayout mainCard = new LinearLayout(this);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        mainCardParams.setMargins(0, 0, 0, dp(20));
        mainCard.setLayoutParams(mainCardParams);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setGravity(Gravity.CENTER);
        mainCard.setPadding(dp(30), dp(40), dp(30), dp(40));

        GradientDrawable mainCardBg = new GradientDrawable();
        mainCardBg.setColor(Color.parseColor("#141414"));
        mainCardBg.setCornerRadius(dp(24));
        mainCard.setBackground(mainCardBg);

        View accentLine = new View(this);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(3)
        );
        lineParams.setMargins(dp(0), dp(0), dp(0), dp(30));
        accentLine.setLayoutParams(lineParams);
        accentLine.setBackgroundColor(Color.parseColor(COLOR_RED));
        mainCard.addView(accentLine);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("STREAM Ex");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        mainCard.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Free Fire MAX · Private Build");
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSubtitle.setGravity(Gravity.CENTER);
        tvSubtitle.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(8), 0, dp(30));
        tvSubtitle.setLayoutParams(subParams);
        mainCard.addView(tvSubtitle);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        );
        dividerParams.setMargins(0, 0, 0, dp(30));
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(Color.parseColor("#2A2A2A"));
        mainCard.addView(divider);

        // START Button
        siButton = new Button(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(55)
        );
        siButton.setLayoutParams(btnParams);
        siButton.setText("START");
        siButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        siButton.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        siButton.setTypeface(null, Typeface.BOLD);
        siButton.setAllCaps(true);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor(COLOR_RED));
        btnBg.setCornerRadius(dp(14));
        siButton.setBackground(btnBg);

        siButton.setEnabled(false);
        siButton.setAlpha(0.5f);

        mainCard.addView(siButton);

        // --- STREAM MODE TOGGLE ---
        final LinearLayout streamModeLayout = new LinearLayout(this);
        LinearLayout.LayoutParams streamParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        streamParams.setMargins(0, dp(15), 0, 0);
        streamModeLayout.setLayoutParams(streamParams);
        streamModeLayout.setOrientation(LinearLayout.HORIZONTAL);
        streamModeLayout.setGravity(Gravity.CENTER_VERTICAL);
        streamModeLayout.setPadding(dp(15), dp(12), dp(15), dp(12));

        final GradientDrawable streamBg = new GradientDrawable();
        streamBg.setColor(Color.parseColor("#1A1A1A"));
        streamBg.setCornerRadius(dp(12));
        streamBg.setStroke(dp(1), Color.parseColor("#2A2A2A"));
        streamModeLayout.setBackground(streamBg);

        TextView tvStreamLabel = new TextView(this);
        tvStreamLabel.setText("ENABLE STREAM MODE");
        tvStreamLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvStreamLabel.setTypeface(null, Typeface.BOLD);
        tvStreamLabel.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        tvStreamLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        streamModeLayout.addView(tvStreamLabel);

        final TextView tvToggle = new TextView(this);
        tvToggle.setText("OFF");
        tvToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvToggle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvToggle.setTextColor(Color.parseColor("#666666"));
        tvToggle.setPadding(dp(12), dp(4), dp(12), dp(4));

        final GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(Color.parseColor("#2A2A2A"));
        toggleBg.setCornerRadius(dp(8));
        tvToggle.setBackground(toggleBg);
        streamModeLayout.addView(tvToggle);

        streamModeLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isStreamModeEnabled = !isStreamModeEnabled;
                    if (isStreamModeEnabled) {
                        tvToggle.setText("ON");
                        tvToggle.setTextColor(Color.parseColor(COLOR_SUCCESS));
                        toggleBg.setStroke(dp(1), Color.parseColor(COLOR_SUCCESS));
                        streamBg.setStroke(dp(1), Color.parseColor(COLOR_SUCCESS));
                    } else {
                        tvToggle.setText("OFF");
                        tvToggle.setTextColor(Color.parseColor("#666666"));
                        toggleBg.setStroke(0, 0);
                        streamBg.setStroke(dp(1), Color.parseColor("#2A2A2A"));
                    }

                    // If service is already running, update it immediately
                    if (FloatService.mSurfaceViewIo) {
                        FloatService.updateStreamMode(isStreamModeEnabled);
                    }
                }
            });

        mainCard.addView(streamModeLayout);
        mainLayout.addView(mainCard);

        // --- KEY INFO CARD (KEY ONLY) ---
        LinearLayout infoCard = new LinearLayout(this);
        LinearLayout.LayoutParams infoCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoCardParams.setMargins(0, 0, 0, dp(15));
        infoCard.setLayoutParams(infoCardParams);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(20), dp(18), dp(20), dp(18));

        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.parseColor("#1A1A1A"));
        infoBg.setCornerRadius(dp(16));
        infoCard.setBackground(infoBg);

        // Key display only
        TextView tvKeyLabel = new TextView(this);
        tvKeyLabel.setText("KEY");
        tvKeyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvKeyLabel.setTextColor(Color.parseColor("#666666"));
        tvKeyLabel.setTypeface(null, Typeface.BOLD);
        infoCard.addView(tvKeyLabel);

        TextView tvKeyValue = new TextView(this);
        tvKeyValue.setText(maskKey(userLicenseKey));
        tvKeyValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvKeyValue.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        tvKeyValue.setTypeface(Typeface.MONOSPACE);
        tvKeyValue.setPadding(0, dp(2), 0, 0);
        infoCard.addView(tvKeyValue);

        mainLayout.addView(infoCard);

        // --- BOTTOM INFO CARDS ---
        LinearLayout infoRow = new LinearLayout(this);
        LinearLayout.LayoutParams infoRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoRow.setLayoutParams(infoRowParams);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout rootCard = new LinearLayout(this);
        LinearLayout.LayoutParams rootCardParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        rootCardParams.setMargins(0, 0, dp(10), 0);
        rootCard.setLayoutParams(rootCardParams);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        rootCard.setGravity(Gravity.CENTER);
        rootCard.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#1A1A1A"));
        rootBg.setCornerRadius(dp(18));
        rootCard.setBackground(rootBg);

        TextView tvRootLabel = new TextView(this);
        tvRootLabel.setText("ROOT");
        tvRootLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvRootLabel.setGravity(Gravity.CENTER);
        tvRootLabel.setTextColor(Color.parseColor("#666666"));
        rootCard.addView(tvRootLabel);

        TextView tvRootValue = new TextView(this);
        tvRootValue.setText("Required");
        tvRootValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvRootValue.setTypeface(null, Typeface.BOLD);
        tvRootValue.setGravity(Gravity.CENTER);
        tvRootValue.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        rootCard.addView(tvRootValue);

        infoRow.addView(rootCard);

        LinearLayout buildCard = new LinearLayout(this);
        LinearLayout.LayoutParams buildCardParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        buildCardParams.setMargins(dp(10), 0, 0, 0);
        buildCard.setLayoutParams(buildCardParams);
        buildCard.setOrientation(LinearLayout.VERTICAL);
        buildCard.setGravity(Gravity.CENTER);
        buildCard.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable buildBg = new GradientDrawable();
        buildBg.setColor(Color.parseColor("#1A1A1A"));
        buildBg.setCornerRadius(dp(18));
        buildCard.setBackground(buildBg);

        TextView tvBuildLabel = new TextView(this);
        tvBuildLabel.setText("BUILD");
        tvBuildLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBuildLabel.setGravity(Gravity.CENTER);
        tvBuildLabel.setTextColor(Color.parseColor("#666666"));
        buildCard.addView(tvBuildLabel);

        TextView tvBuildValue = new TextView(this);
        tvBuildValue.setText("Private");
        tvBuildValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvBuildValue.setTypeface(null, Typeface.BOLD);
        tvBuildValue.setGravity(Gravity.CENTER);
        tvBuildValue.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        buildCard.addView(tvBuildValue);

        infoRow.addView(buildCard);
        mainLayout.addView(infoRow);

        setContentView(mainLayout);

        // --- START BUTTON CLICK LISTENER ---
        siButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // SECRETLY CHECK EXPIRY
                    if (System.currentTimeMillis() > expiryTimestamp) {
                        showKeyExpiredDialog();
                        return;
                    }

                    if (!FloatService.mSurfaceViewIo && IPCService.isConnect()) {
                        try {
                            FloatService.isStreamMode = isStreamModeEnabled;
                            FloatService.ShowFloat(MainActivity.this);
                            IPCService.GetIPC().SetSavePath(getFilesDir().toString());
                            Toast.makeText(MainActivity.this, "SERVICE STARTED!", Toast.LENGTH_SHORT).show();
                        } catch (RemoteException e) {
                            Toast.makeText(MainActivity.this, "ERROR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else if (FloatService.mSurfaceViewIo) {
                        Toast.makeText(MainActivity.this, "SERVICE ALREADY RUNNING!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "IPC SERVICE NOT CONNECTED!", Toast.LENGTH_SHORT).show();
                    }

                    // LAUNCH FREE FIRE MAX
                    try {
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.dts.freefiremax");
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                        } else {
                            Toast.makeText(MainActivity.this, "FREE FIRE MAX NOT INSTALLED!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "CANNOT OPEN FREE FIRE MAX", Toast.LENGTH_SHORT).show();
                    }
                }
            });

        // Thread for updating button text
        new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (IPCService.isConnect()) {
                            runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        siButton.setText("START");
                                        siButton.setEnabled(true);
                                        siButton.setAlpha(1.0f);
                                    }
                                });
                            break;
                        }
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
    }

    private String getMediaDrmId() {
        android.media.MediaDrm mediaDrm = null;
        try {
            java.util.UUID widevineUuid = new java.util.UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
            mediaDrm = new android.media.MediaDrm(widevineUuid);
            byte[] deviceUniqueId = mediaDrm.getPropertyByteArray(android.media.MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);

            if (deviceUniqueId == null) return null;

            StringBuilder sb = new StringBuilder();
            for (byte b : deviceUniqueId) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (mediaDrm != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    mediaDrm.close();
                } else {
                    mediaDrm.release();
                }
            }
        }
    }
}
