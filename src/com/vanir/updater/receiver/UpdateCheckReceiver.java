/*
 * Copyright (C) 2012 The Vanir Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.vanir.updater.receiver;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;

import com.vanir.updater.R;
import com.vanir.updater.misc.Constants;
import com.vanir.updater.service.UpdateCheckService;
import com.vanir.updater.utils.Utils;

public class UpdateCheckReceiver extends BroadcastReceiver {
    private static final String TAG = "UpdateCheckReceiver";
    private static final String GMS_CORE = "com.google.android.gms";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Load the required settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int updateFrequency = prefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);

        // Parse the received action
        final String action = intent.getAction();

        if (updateFrequency == Constants.UPDATE_FREQ_NONE) {
            if (!Intent.ACTION_CHECK_FOR_UPDATES.equals(action)) {
                return;
            }
        }

        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            // Connectivity has changed
            boolean hasConnection = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            Log.i(TAG, "Got connectivity change, has connection: " + hasConnection);
            if (!hasConnection) {
                return;
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // We just booted. Store the boot check state
            prefs.edit().putBoolean(Constants.BOOT_CHECK_COMPLETED, false).apply();
            // Check for the existance of GApps
            PackageManager pm = context.getPackageManager();
            try {
                pm.getPackageInfo(GMS_CORE, PackageManager.GET_ACTIVITIES);
            } catch (NameNotFoundException e) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.app_name);
                builder.setMessage(R.string.gapps_not_installed);
                builder.setPositiveButton(R.string.dialog_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                dialog.show();
            }
        }
        if (Intent.ACTION_CHECK_FOR_UPDATES.equals(action)) {
            Log.i(TAG, "Received quicksettings check request");
            Intent i = new Intent(context, UpdateCheckService.class);
            i.setAction(UpdateCheckService.ACTION_CHECK);
            i.putExtra("isFromQuicksettings", 1);
            context.startService(i);
            return;
        }
        // Handle the actual update check based on the defined frequency
        if (updateFrequency == Constants.UPDATE_FREQ_AT_BOOT) {
            boolean bootCheckCompleted = prefs.getBoolean(Constants.BOOT_CHECK_COMPLETED, false);
            if (!bootCheckCompleted) {
                Log.i(TAG, "Start an on-boot check");
                Intent i = new Intent(context, UpdateCheckService.class);
                i.setAction(UpdateCheckService.ACTION_CHECK);
                context.startService(i);
            } else {
                // Nothing to do
                Log.i(TAG, "On-boot update check was already completed.");
                return;
            }
        } else if (updateFrequency > 0) {
            Log.i(TAG, "Scheduling future, repeating update checks.");
            Utils.scheduleUpdateService(context, updateFrequency * 1000);
        }
    }
}
