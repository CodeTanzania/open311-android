package com.github.codetanzania.util;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.github.codetanzania.model.Jurisdiction;
import com.github.codetanzania.model.Reporter;

public class Util {

    public static final String TAG = "Util";

    public enum RunningMode {
        FIRST_TIME_INSTALL,
        FIRST_TIME_UPGRADE
    }



    public static boolean isFirstRun(Context mContext, RunningMode mRunningMode) throws Exception {

        int currentVersionCode, savedVersionCode;

        try {
            currentVersionCode = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0)
                    .versionCode;

        } catch(android.content.pm.PackageManager.NameNotFoundException e) {
            Log.e(TAG, String.format("An exception is %s", e.getMessage()));
            throw new Exception(
                    String.format("Package name not found. Original exception was: %s ", e.getMessage()));
        }

        SharedPreferences sharedPrefs = mContext
                .getSharedPreferences(AppConfig.Const.KEY_SHARED_PREFS, Context.MODE_PRIVATE);

        savedVersionCode = sharedPrefs.getInt(AppConfig.Const.APP_VERSION_CODE, -1);

        boolean firstTimeRun = savedVersionCode == -1;
        boolean upgradeRun   = savedVersionCode <  currentVersionCode;

        if (firstTimeRun || upgradeRun) {
            sharedPrefs.edit().putInt(
                    AppConfig.Const.APP_VERSION_CODE, currentVersionCode).apply();
        }

        if (mRunningMode == RunningMode.FIRST_TIME_INSTALL) {
            return firstTimeRun;
        } else {
            return mRunningMode == RunningMode.FIRST_TIME_UPGRADE && upgradeRun;
        }
    }

    public static Reporter getCurrentReporter(Context mContext) {
        SharedPreferences sharedPrefs = mContext.getSharedPreferences(
                AppConfig.Const.KEY_SHARED_PREFS, Context.MODE_PRIVATE);
        String phone = sharedPrefs.getString(AppConfig.Const.REPORTER_PHONE, null);

        // logical to use phone number which we verify through OTP
        if (phone == null) {
            return null;
        }

        String email = sharedPrefs.getString(AppConfig.Const.REPORTER_EMAIL, null);
        String account = sharedPrefs.getString(AppConfig.Const.REPORTER_DAWASCO_ACCOUNT, null);
        String fullName = sharedPrefs.getString(AppConfig.Const.REPORTER_NAME, null);

        Reporter reporter = new Reporter();
        reporter.account = account;
        reporter.phone = phone;
        reporter.name = fullName;
        reporter.email = email;

        return reporter;
    }

    public static void storeCurrentReporter(Context mContext, Reporter reporter) {
        SharedPreferences sharedPrefs = mContext.getSharedPreferences(
                AppConfig.Const.KEY_SHARED_PREFS, Context.MODE_PRIVATE);
        sharedPrefs.edit()
                .putString(AppConfig.Const.REPORTER_NAME, reporter.name)
                .putString(AppConfig.Const.REPORTER_PHONE, reporter.phone)
                .putString(AppConfig.Const.REPORTER_EMAIL, reporter.email)
                .putString(AppConfig.Const.REPORTER_DAWASCO_ACCOUNT, reporter.account)
                .apply();
    }

    public static Jurisdiction getReporterJurisdiction(Context mContext) {
        throw new UnsupportedOperationException("method not implemented yet");
    }

    public static void storeReporterJurisdiction(Context mContext, Jurisdiction jurisdiction) {
        throw new UnsupportedOperationException("method not implemented yet");
    }

    public static void resetPreferences(Context mContext) {
        mContext.getSharedPreferences(
                AppConfig.Const.KEY_SHARED_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
