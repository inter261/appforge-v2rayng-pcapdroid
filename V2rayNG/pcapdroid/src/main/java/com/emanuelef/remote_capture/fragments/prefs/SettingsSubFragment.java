/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PCAPdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PCAPdroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2020-26 - Emanuele Faranda
 */

package com.emanuelef.remote_capture.fragments.prefs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.emanuelef.remote_capture.Utils;
import com.emanuelef.remote_capture.interfaces.FragmentViewCreatedListener;

public abstract class SettingsSubFragment extends PreferenceFragmentCompat {
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // insets handling logic
        view.setFitsSystemWindows(true);

        // fix for missing insets dispatching
        if (getActivity() instanceof FragmentViewCreatedListener)
            ((FragmentViewCreatedListener) requireActivity()).onFragmentViewCreated(view);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        Intent intent = preference.getIntent();
        if (intent != null) {
            Utils.startActivity(requireContext(), intent);
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }
}
