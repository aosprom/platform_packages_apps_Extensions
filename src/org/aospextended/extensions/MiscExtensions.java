/*
 * Copyright (C) 2017 AospExtended ROM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aospextended.extensions;

import android.app.AlertDialog;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManagerGlobal;
import android.view.IWindowManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Locale;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.os.Handler;
import android.widget.EditText;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.Utils;
import java.util.Date;

public class MiscExtensions extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String STATUS_BAR_DATE = "status_bar_date";
    private static final String STATUS_BAR_DATE_STYLE = "status_bar_date_style";
    private static final String STATUS_BAR_DATE_FORMAT = "status_bar_date_format";

    public static final int CLOCK_DATE_STYLE_LOWERCASE = 1;
    public static final int CLOCK_DATE_STYLE_UPPERCASE = 2;
    private static final int CUSTOM_CLOCK_DATE_FORMAT_INDEX = 18;

    private ListPreference mStatusBarDate;
    private ListPreference mStatusBarDateStyle;
    private ListPreference mStatusBarDateFormat;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.misc_extensions);

        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefSet = getPreferenceScreen();

        mStatusBarDate = (ListPreference) findPreference(STATUS_BAR_DATE);
	    int clockDateDisplay = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_DATE, 0);
        mStatusBarDate.setValue(String.valueOf(clockDateDisplay));
        mStatusBarDate.setSummary(mStatusBarDate.getEntry());
        mStatusBarDate.setOnPreferenceChangeListener(this);

	    mStatusBarDateStyle = (ListPreference) findPreference(STATUS_BAR_DATE_STYLE);
        int clockDateStyle = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_DATE_STYLE, 0);
        mStatusBarDateStyle.setValue(String.valueOf(clockDateStyle));
        mStatusBarDateStyle.setSummary(mStatusBarDateStyle.getEntry());
        mStatusBarDateStyle.setOnPreferenceChangeListener(this);
  
        mStatusBarDateFormat = (ListPreference) findPreference(STATUS_BAR_DATE_FORMAT);
        mStatusBarDateFormat.setOnPreferenceChangeListener(this);
        if (mStatusBarDateFormat.getValue() == null) {
            mStatusBarDateFormat.setValue("EEEE");
        }
        parseClockDateFormats();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXTENSIONS;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {

	AlertDialog dialog;
        if (preference == mStatusBarDate) {
            int clockDateDisplay = Integer.valueOf((String) objValue);
            int index = mStatusBarDate.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.STATUS_BAR_DATE, clockDateDisplay);
            mStatusBarDate.setSummary(mStatusBarDate.getEntries()[index]);
            if (clockDateDisplay == 0) {
                mStatusBarDateStyle.setEnabled(false);
                mStatusBarDateFormat.setEnabled(false);
            } else {
                mStatusBarDateStyle.setEnabled(true);
                mStatusBarDateFormat.setEnabled(true);
            }
            return true;
        } else if (preference == mStatusBarDateStyle) {
            int clockDateStyle = Integer.valueOf((String) objValue);
            int index = mStatusBarDateStyle.findIndexOfValue((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.STATUS_BAR_DATE_STYLE, clockDateStyle);
            mStatusBarDateStyle.setSummary(mStatusBarDateStyle.getEntries()[index]);
            parseClockDateFormats();
            return true;
        } else if (preference == mStatusBarDateFormat) {
            int index = mStatusBarDateFormat.findIndexOfValue((String) objValue);

            if (index == CUSTOM_CLOCK_DATE_FORMAT_INDEX) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.status_bar_date_string_edittext_title);
                alert.setMessage(R.string.status_bar_date_string_edittext_summary);

                final EditText input = new EditText(getActivity());
                String oldText = Settings.System.getString(
                    getActivity().getContentResolver(),
                    Settings.System.STATUS_BAR_DATE_FORMAT);
                if (oldText != null) {
                    input.setText(oldText);
                }
                alert.setView(input);

                alert.setPositiveButton(R.string.menu_save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int whichButton) {
                        String value = input.getText().toString();
                        if (value.equals("")) {
                            return;
                        }
                        Settings.System.putString(getActivity().getContentResolver(),
                            Settings.System.STATUS_BAR_DATE_FORMAT, value);

                        return;
                    }
                });

                alert.setNegativeButton(R.string.menu_cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        return;
                    }
                });
                dialog = alert.create();
                dialog.show();
            } else {
                if ((String) objValue != null) {
                    Settings.System.putString(getActivity().getContentResolver(),
                        Settings.System.STATUS_BAR_DATE_FORMAT, (String) objValue);
                }
            }
            return true;
         }
         return false;
    }

    private void parseClockDateFormats() {
        // Parse and repopulate mStatusBarDateFormat's entries based on current date.
        String[] dateEntries = getResources().getStringArray(R.array.status_bar_date_format_entries_values);
        CharSequence parsedDateEntries[];
        parsedDateEntries = new String[dateEntries.length];
        Date now = new Date();

        int lastEntry = dateEntries.length - 1;
        int dateFormat = Settings.System.getInt(getActivity()
                .getContentResolver(), Settings.System.STATUS_BAR_DATE_STYLE, 0);
        for (int i = 0; i < dateEntries.length; i++) {
            if (i == lastEntry) {
                parsedDateEntries[i] = dateEntries[i];
            } else {
                String newDate;
                CharSequence dateString = DateFormat.format(dateEntries[i], now);
                if (dateFormat == CLOCK_DATE_STYLE_LOWERCASE) {
                    newDate = dateString.toString().toLowerCase();
                } else if (dateFormat == CLOCK_DATE_STYLE_UPPERCASE) {
                    newDate = dateString.toString().toUpperCase();
                } else {
                    newDate = dateString.toString();
                }

                parsedDateEntries[i] = newDate;
            }
        }
        mStatusBarDateFormat.setEntries(parsedDateEntries);
    }
}
