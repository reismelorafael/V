package com.vectras.vm.utils;

import static android.content.Intent.ACTION_VIEW;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.vectras.vm.R;

public class DialogUtils {

    /**
     * Shows a progress dialog for long-running operations.
     * The dialog shows an indeterminate progress bar with a message.
     * 
     * @param context The context (must be an Activity that is not finishing/destroyed)
     * @param title Dialog title
     * @param message Dialog message
     * @return The AlertDialog instance (call dismiss() when operation completes), 
     *         or null if context is invalid (activity is finishing/destroyed).
     *         Callers should check for null before using the returned dialog.
     */
    public static AlertDialog showProgressDialog(Context context, String title, String message) {
        if (!isAllowShow(context)) {
            // Context is not valid for showing dialog (activity finishing/destroyed)
            return null;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress_operation, null);
        
        TextView titleView = view.findViewById(R.id.progress_title);
        TextView messageView = view.findViewById(R.id.progress_message);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        
        if (titleView != null) titleView.setText(title);
        if (messageView != null) messageView.setText(message);
        if (progressBar != null) progressBar.setIndeterminate(true);
        
        builder.setView(view);
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    /**
     * Shows a progress dialog with determinate progress.
     * Call updateProgressDialog() to update progress.
     * 
     * @param context The context (must be an Activity that is not finishing/destroyed)
     * @param title Dialog title
     * @param message Dialog message
     * @param max Maximum progress value
     * @return The AlertDialog instance, or null if context is invalid.
     *         Callers should check for null before using the returned dialog.
     */
    public static AlertDialog showProgressDialog(Context context, String title, String message, int max) {
        if (!isAllowShow(context)) {
            // Context is not valid for showing dialog (activity finishing/destroyed)
            return null;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress_operation, null);
        
        TextView titleView = view.findViewById(R.id.progress_title);
        TextView messageView = view.findViewById(R.id.progress_message);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        
        if (titleView != null) titleView.setText(title);
        if (messageView != null) messageView.setText(message);
        if (progressBar != null) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(max);
            progressBar.setProgress(0);
        }
        
        builder.setView(view);
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    /**
     * Updates the progress of a determinate progress dialog.
     * 
     * @param dialog The dialog to update
     * @param progress Current progress value
     * @param message Optional new message (pass null to keep current)
     */
    public static void updateProgressDialog(AlertDialog dialog, int progress, String message) {
        if (dialog == null || !dialog.isShowing()) return;
        
        ProgressBar progressBar = dialog.findViewById(R.id.progress_bar);
        TextView messageView = dialog.findViewById(R.id.progress_message);
        
        if (progressBar != null) progressBar.setProgress(progress);
        if (messageView != null && message != null) messageView.setText(message);
    }

    public static void oneDialog(Context context, String title, String message, int iconid) {
        oneDialog(context, title, message, context.getString(R.string.ok), iconid != -1, iconid, true, null, null);
    }

    public static void oneDialog(Context context, String _title, String _message, String _textPositiveButton, boolean _isicon, int _iconid, boolean _cancel, Runnable _onPositive, Runnable _onDismiss) {
        if (!isAllowShow(context)) return;

        View buttonsView = LayoutInflater.from(context).inflate(R.layout.dialog_layout, null);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setCancelable(_cancel);
        dialog.setView(buttonsView);

        ImageView icon = buttonsView.findViewById(R.id.icon);
        TextView title = buttonsView.findViewById(R.id.tv_title);
        TextView content = buttonsView.findViewById(R.id.tv_content);
        TextView positiveButton = buttonsView.findViewById(R.id.positiveButton);
        TextView negativeButton = buttonsView.findViewById(R.id.negativeButton);
        TextView neutralButton = buttonsView.findViewById(R.id.neutralButton);

        if (_isicon) {
            icon.setImageResource(_iconid);
        } else {
            icon.setVisibility(View.GONE);
        }

        if (UIUtils.isUsingThemeNightMode()
                || !UIUtils.isColorLight(MaterialColors.getColor(positiveButton, com.google.android.material.R.attr.colorPrimaryContainer)))
            positiveButton.setTextColor(Color.WHITE);

        title.setText(_title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Spannable sp = (Spannable) (isHTML(_message) ?
                    Html.fromHtml(_message, Html.FROM_HTML_MODE_LEGACY) :
                    new SpannableString(_message));
            Linkify.addLinks(sp, Linkify.ALL);
            content.setText(sp);
            content.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            content.setText(_message);
        }


        positiveButton.setText(_textPositiveButton);
        positiveButton.setBackgroundResource(R.drawable.dialog_shape_single_button);
        negativeButton.setVisibility(View.GONE);
        neutralButton.setVisibility(View.GONE);

        positiveButton.setOnClickListener(v -> {
            if (_onPositive != null) _onPositive.run();
            dialog.dismiss();
        });

//        dialog.setPositiveButton(_textPositiveButton, (dialog2, which) -> {
//            if (_onPositive != null) _onPositive.run();
//            dialog2.dismiss();
//        });
        dialog.setOnDismissListener(dialog1 -> {
            if (_onDismiss != null) _onDismiss.run();
        });
        dialog.show();
    }

    public static void twoDialog(Context context, String _title, String _message, String _textPositiveButton, String _textNegativeButton, boolean _isicon, int _iconid, boolean _cancel, Runnable _onPositive, Runnable _onNegative, Runnable _onDismiss) {
        if (!isAllowShow(context)) return;

        View buttonsView = LayoutInflater.from(context).inflate(R.layout.dialog_layout, null);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setCancelable(_cancel);
        dialog.setView(buttonsView);

        ImageView icon = buttonsView.findViewById(R.id.icon);
        TextView title = buttonsView.findViewById(R.id.tv_title);
        TextView content = buttonsView.findViewById(R.id.tv_content);
        TextView positiveButton = buttonsView.findViewById(R.id.positiveButton);
        TextView negativeButton = buttonsView.findViewById(R.id.negativeButton);
        TextView neutralButton = buttonsView.findViewById(R.id.neutralButton);

        if (_isicon) {
            icon.setImageResource(_iconid);
        } else {
            icon.setVisibility(View.GONE);
        }

        if (UIUtils.isUsingThemeNightMode()
                || !UIUtils.isColorLight(MaterialColors.getColor(positiveButton, com.google.android.material.R.attr.colorPrimaryContainer))) {
            positiveButton.setTextColor(Color.WHITE);
            negativeButton.setTextColor(Color.WHITE);
        }

        title.setText(_title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Spannable sp = (Spannable) (isHTML(_message) ?
                    Html.fromHtml(_message, Html.FROM_HTML_MODE_LEGACY) :
                    new SpannableString(_message));
            Linkify.addLinks(sp, Linkify.ALL);
            content.setText(sp);
            content.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            content.setText(_message);
        }

        positiveButton.setText(_textPositiveButton);
        negativeButton.setText(_textNegativeButton);
        negativeButton.setBackgroundResource(R.drawable.dialog_shape_bottom_button);
        neutralButton.setVisibility(View.GONE);

        positiveButton.setOnClickListener(v -> {
            if (_onPositive != null) _onPositive.run();
            dialog.dismiss();
        });

        negativeButton.setOnClickListener(v -> {
            if (_onNegative != null) _onNegative.run();
            dialog.dismiss();
        });
//        dialog.setPositiveButton(_textPositiveButton, (dialog2, which) -> {
//            if (_onPositive != null) _onPositive.run();
//            dialog2.dismiss();
//        });
//        dialog.setNegativeButton(_textNegativeButton, (dialog3, which) -> {
//            if (_onNegative != null) _onNegative.run();
//            dialog3.dismiss();
//        });
        dialog.setOnDismissListener(dialog1 -> {
            if (_onDismiss != null) _onDismiss.run();
        });
        dialog.show();
    }

    public static void threeDialog(Context context, String _title, String _message, String _textPositiveButton, String _textNegativeButton, String _textNeutralButton, boolean _isicon, int _iconid, boolean _cancel, Runnable _onPositive, Runnable _onNegative, Runnable _onNeutral, Runnable _onDismiss) {
        if (!isAllowShow(context)) return;

        View buttonsView = LayoutInflater.from(context).inflate(R.layout.dialog_layout, null);

        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setCancelable(_cancel);
        dialog.setView(buttonsView);

        ImageView icon = buttonsView.findViewById(R.id.icon);
        TextView title = buttonsView.findViewById(R.id.tv_title);
        TextView content = buttonsView.findViewById(R.id.tv_content);
        TextView positiveButton = buttonsView.findViewById(R.id.positiveButton);
        TextView negativeButton = buttonsView.findViewById(R.id.negativeButton);
        TextView neutralButton = buttonsView.findViewById(R.id.neutralButton);

        if (_isicon) {
            icon.setImageResource(_iconid);
        } else {
            icon.setVisibility(View.GONE);
        }

        title.setText(_title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Spannable sp = (Spannable) (isHTML(_message) ?
                    Html.fromHtml(_message, Html.FROM_HTML_MODE_LEGACY) :
                    new SpannableString(_message));
            Linkify.addLinks(sp, Linkify.ALL);
            content.setText(sp);
            content.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            content.setText(_message);
        }

        if (UIUtils.isUsingThemeNightMode()
                || !UIUtils.isColorLight(MaterialColors.getColor(positiveButton, com.google.android.material.R.attr.colorPrimaryContainer))) {
            positiveButton.setTextColor(Color.WHITE);
            negativeButton.setTextColor(Color.WHITE);
            neutralButton.setTextColor(Color.WHITE);
        }

        positiveButton.setText(_textPositiveButton);
        negativeButton.setText(_textNegativeButton);
        neutralButton.setText(_textNeutralButton);

        positiveButton.setOnClickListener(v -> {
            if (_onPositive != null) _onPositive.run();
            dialog.dismiss();
        });

        negativeButton.setOnClickListener(v -> {
            if (_onNegative != null) _onNegative.run();
            dialog.dismiss();
        });

        neutralButton.setOnClickListener(v -> {
            if (_onNeutral != null) _onNeutral.run();
            dialog.dismiss();
        });

//        dialog.setPositiveButton(_textPositiveButton, (dialog2, which) -> {
//            if (_onPositive != null) _onPositive.run();
//            dialog2.dismiss();
//        });
//        dialog.setNegativeButton(_textNegativeButton, (dialog3, which) -> {
//            if (_onNegative != null) _onNegative.run();
//            dialog3.dismiss();
//        });
//        dialog.setNeutralButton(_textNeutralButton, (dialog4, which) -> {
//            if (_onNeutral != null) _onNeutral.run();
//            dialog4.dismiss();
//        });
        dialog.setOnDismissListener(dialog1 -> {
            if (_onDismiss != null) _onDismiss.run();
        });
        dialog.show();
    }

    public static boolean isAllowShow(Context context) {
        if (context instanceof Activity activity) {
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return false;
    }

    public static void needInstallTermuxX11(Context _context) {
        twoDialog(_context, _context.getResources().getString(R.string.action_needed),
                _context.getResources().getString(R.string.need_install_termux_x11_content),
                _context.getResources().getString(R.string.install), _context.getResources().getString(R.string.cancel),
                true, R.drawable.warning_24px, true,
                () -> {
                    String tg = "https://github.com/termux/termux-x11/releases";
                    Intent f = new Intent(ACTION_VIEW);
                    f.setData(Uri.parse(tg));
                    _context.startActivity(f);
                }, null, null);
    }

    public static void fileDeletionResult(Context _context, boolean isCompleted) {
        if (isCompleted) {
            DialogUtils.oneDialog(
                    _context,
                    _context.getString(R.string.done),
                    _context.getString(R.string.file_deleted),
                    _context.getString(R.string.ok),
                    true,
                    R.drawable.check_24px,
                    true,
                    null,
                    null
            );
        } else {
            DialogUtils.oneDialog(
                    _context,
                    _context.getString(R.string.oops),
                    _context.getString(R.string.delete_file_failed_content),
                    _context.getString(R.string.ok),
                    true,
                    R.drawable.error_96px,
                    true,
                    null,
                    null
            );
        }
    }

    public static boolean isHTML(String content) {
        return content.contains("<") && content.contains("</") && content.contains(">");
    }
}
