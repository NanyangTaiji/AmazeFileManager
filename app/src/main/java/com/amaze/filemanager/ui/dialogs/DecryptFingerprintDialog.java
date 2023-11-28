package com.amaze.filemanager.ui.dialogs;

import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.view.View;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatButton;
import com.afollestad.materialdialogs.MaterialDialog;
import com.amaze.filemanager.R;
import com.amaze.filemanager.filesystem.files.CryptUtil;
import com.amaze.filemanager.filesystem.files.EncryptDecryptUtils;
import com.amaze.filemanager.ui.activities.MainActivity;
import com.amaze.filemanager.ui.theme.AppTheme;
import com.amaze.filemanager.utils.FingerprintHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Decrypt dialog prompt for user fingerprint.
 */
public class DecryptFingerprintDialog {

    /**
     * Display dialog prompting user for fingerprint in order to decrypt file.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void show(
            Context context,
            MainActivity mainActivity,
            Intent intent,
            AppTheme appTheme,
            EncryptDecryptUtils.DecryptButtonCallbackInterface decryptButtonCallbackInterface)
            throws GeneralSecurityException, IOException {

        int accentColor = mainActivity.getAccent();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(context);
        builder.title(context.getString(R.string.crypt_decrypt));
        View rootView = View.inflate(context, R.layout.dialog_decrypt_fingerprint_authentication, null);
        AppCompatButton cancelButton = rootView.findViewById(R.id.button_decrypt_fingerprint_cancel);
        cancelButton.setTextColor(accentColor);
        builder.customView(rootView, true);
        builder.canceledOnTouchOutside(false);
        builder.theme(appTheme.getMaterialDialogTheme(context));
        MaterialDialog dialog = builder.show();

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        FingerprintManager manager = context.getSystemService(FingerprintManager.class);
        FingerprintHandler handler = new FingerprintHandler(context, intent, dialog, decryptButtonCallbackInterface);
        FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(CryptUtil.initCipher());
        handler.authenticate(manager, cryptoObject);
    }
}

