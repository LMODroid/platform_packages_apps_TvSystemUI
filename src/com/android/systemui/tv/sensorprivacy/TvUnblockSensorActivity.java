/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.tv.sensorprivacy;

import android.annotation.DimenRes;
import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorPrivacyManager;
import android.hardware.SensorPrivacyManager.Sensors;
import android.hardware.SensorPrivacyManager.Sources;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.IndividualSensorPrivacyController;
import com.android.systemui.tv.TvBottomSheetActivity;

import java.util.List;

import javax.inject.Inject;

/**
 * Bottom sheet that is shown when the camera/mic sensors are blocked by the global software toggle
 * or physical privacy switch.
 */
public class TvUnblockSensorActivity extends TvBottomSheetActivity {

    private static final String TAG = TvUnblockSensorActivity.class.getSimpleName();
    private static final String ACTION_MANAGE_CAMERA_PRIVACY =
            "android.settings.MANAGE_CAMERA_PRIVACY";
    private static final String ACTION_MANAGE_MICROPHONE_PRIVACY =
            "android.settings.MANAGE_MICROPHONE_PRIVACY";

    private static final int ALL_SENSORS = Integer.MAX_VALUE;

    private int mSensor = -1;

    private final AppOpsManager mAppOpsManager;
    private final RoleManager mRoleManager;
    private final IndividualSensorPrivacyController mSensorPrivacyController;
    private IndividualSensorPrivacyController.Callback mSensorPrivacyCallback;
    private TextView mTitle;
    private TextView mContent;
    private ImageView mIcon;
    private ImageView mSecondIcon;
    private Button mPositiveButton;
    private Button mCancelButton;

    @Inject
    public TvUnblockSensorActivity(
            IndividualSensorPrivacyController individualSensorPrivacyController,
            AppOpsManager appOpsManager, RoleManager roleManager) {
        mSensorPrivacyController = individualSensorPrivacyController;
        mAppOpsManager = appOpsManager;
        mRoleManager = roleManager;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        boolean allSensors = getIntent().getBooleanExtra(SensorPrivacyManager.EXTRA_ALL_SENSORS,
                false);
        if (allSensors) {
            mSensor = ALL_SENSORS;
        } else {
            mSensor = getIntent().getIntExtra(SensorPrivacyManager.EXTRA_SENSOR, -1);
        }

        if (mSensor == -1) {
            Log.v(TAG, "Invalid extras");
            finish();
            return;
        }

        mSensorPrivacyCallback = (sensor, blocked) -> {
            if (mSensor == ALL_SENSORS && !mSensorPrivacyController.isSensorBlocked(Sensors.CAMERA)
                    && !mSensorPrivacyController.isSensorBlocked(Sensors.MICROPHONE)) {
                showToastAndFinish();
            } else if (this.mSensor == sensor && !blocked) {
                showToastAndFinish();
            } else {
                updateUI();
            }
        };

        initUI();
    }

    private void showToastAndFinish() {
        final int toastMsgResId;
        switch(mSensor) {
            case Sensors.MICROPHONE:
                toastMsgResId = R.string.sensor_privacy_mic_unblocked_toast_content;
                break;
            case Sensors.CAMERA:
                toastMsgResId = R.string.sensor_privacy_camera_unblocked_toast_content;
                break;
            case ALL_SENSORS:
            default:
                toastMsgResId = R.string.sensor_privacy_mic_camera_unblocked_toast_content;
                break;
        }
        showToastAndFinish(toastMsgResId);
    }

    private void showToastAndFinish(int toastMsgResId) {
        Toast.makeText(this, toastMsgResId, Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean isBlockedByHardwareToggle() {
        if (mSensor == ALL_SENSORS) {
            return mSensorPrivacyController.isSensorBlockedByHardwareToggle(Sensors.CAMERA)
                    || mSensorPrivacyController.isSensorBlockedByHardwareToggle(Sensors.MICROPHONE);
        } else {
            return mSensorPrivacyController.isSensorBlockedByHardwareToggle(mSensor);
        }
    }

    private void initUI() {
        mTitle = findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_title);
        mContent = findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_body);
        mIcon = findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_icon);
        // mic icon if both icons are shown
        mSecondIcon = findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_second_icon);
        mPositiveButton =
                findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_positive_button);
        mCancelButton = findViewById(com.android.systemui.tv.res.R.id.bottom_sheet_negative_button);

        mCancelButton.setText(android.R.string.cancel);
        mCancelButton.setOnClickListener(v -> finish());

        updateUI();
    }

    private void updateUI() {
        if (isHTTAccessDisabled()) {
            updateUiForHTT();
        } else if (isBlockedByHardwareToggle()) {
            updateUiForHardwareToggle();
        } else {
            updateUiForSoftwareToggle();
        }
    }

    private void updateUiForHardwareToggle() {
        final Resources resources = getResources();

        boolean micBlocked = (mSensor == Sensors.MICROPHONE || mSensor == ALL_SENSORS)
                && mSensorPrivacyController.isSensorBlockedByHardwareToggle(Sensors.MICROPHONE);
        boolean cameraBlocked = (mSensor == Sensors.CAMERA || mSensor == ALL_SENSORS)
                && mSensorPrivacyController.isSensorBlockedByHardwareToggle(Sensors.CAMERA);

        setIconTint(
                resources.getBoolean(
                        com.android.systemui.tv.res.R.bool.config_unblockHwSensorIconEnableTint));
        setIconSize(
                com.android.systemui.tv.res.R.dimen.unblock_hw_sensor_icon_width,
                com.android.systemui.tv.res.R.dimen.unblock_hw_sensor_icon_height);

        if (micBlocked && cameraBlocked) {
            mTitle.setText(R.string.sensor_privacy_start_use_mic_camera_blocked_dialog_title);
            mContent.setText(
                    R.string.sensor_privacy_start_use_mic_camera_blocked_dialog_content);
            mIcon.setImageResource(R.drawable.unblock_hw_sensor_all);

            Drawable secondIconDrawable = resources.getDrawable(
                    R.drawable.unblock_hw_sensor_all_second, getTheme());
            if (secondIconDrawable == null) {
                mSecondIcon.setVisibility(View.GONE);
            } else {
                mSecondIcon.setImageDrawable(secondIconDrawable);
            }
        } else if (cameraBlocked) {
            mTitle.setText(R.string.sensor_privacy_start_use_camera_blocked_dialog_title);
            mContent.setText(R.string.sensor_privacy_start_use_camera_blocked_dialog_content);
            mIcon.setImageResource(R.drawable.unblock_hw_sensor_camera);
            mSecondIcon.setVisibility(View.GONE);
        } else if (micBlocked) {
            mTitle.setText(R.string.sensor_privacy_start_use_mic_blocked_dialog_title);
            mContent.setText(R.string.sensor_privacy_start_use_mic_blocked_dialog_content);
            mIcon.setImageResource(R.drawable.unblock_hw_sensor_microphone);
            mSecondIcon.setVisibility(View.GONE);
        }

        // Start animation if drawable is animated
        Drawable iconDrawable = mIcon.getDrawable();
        if (iconDrawable instanceof Animatable) {
            ((Animatable) iconDrawable).start();
        }

        mPositiveButton.setVisibility(View.GONE);
        mCancelButton.setText(android.R.string.ok);
    }

    private void updateUiForSoftwareToggle() {
        setIconTint(true);
        setIconSize(
                com.android.systemui.tv.res.R.dimen.bottom_sheet_icon_size,
                com.android.systemui.tv.res.R.dimen.bottom_sheet_icon_size);

        switch (mSensor) {
            case Sensors.MICROPHONE:
                mTitle.setText(R.string.sensor_privacy_start_use_mic_blocked_dialog_title);
                mContent.setText(R.string.sensor_privacy_start_use_mic_dialog_content);
                mIcon.setImageResource(com.android.internal.R.drawable.perm_group_microphone);
                mSecondIcon.setVisibility(View.GONE);
                break;
            case Sensors.CAMERA:
                mTitle.setText(R.string.sensor_privacy_start_use_camera_blocked_dialog_title);
                mContent.setText(R.string.sensor_privacy_start_use_camera_dialog_content);
                mIcon.setImageResource(com.android.internal.R.drawable.perm_group_camera);
                mSecondIcon.setVisibility(View.GONE);
                break;
            case ALL_SENSORS:
            default:
                mTitle.setText(R.string.sensor_privacy_start_use_mic_camera_blocked_dialog_title);
                mContent.setText(R.string.sensor_privacy_start_use_mic_camera_dialog_content);
                mIcon.setImageResource(com.android.internal.R.drawable.perm_group_camera);
                mSecondIcon.setImageResource(
                        com.android.internal.R.drawable.perm_group_microphone);
                break;
        }

        mPositiveButton.setText(
                com.android.internal.R.string.sensor_privacy_start_use_dialog_turn_on_button);
        mPositiveButton.setOnClickListener(v -> {
            if (mSensor == ALL_SENSORS) {
                mSensorPrivacyController.setSensorBlocked(Sources.OTHER, Sensors.CAMERA, false);
                mSensorPrivacyController.setSensorBlocked(Sources.OTHER, Sensors.MICROPHONE, false);
            } else {
                mSensorPrivacyController.setSensorBlocked(Sources.OTHER, mSensor, false);
            }
        });
    }

    private void updateUiForHTT() {
        setIconTint(true);
        setIconSize(
                com.android.systemui.tv.res.R.dimen.bottom_sheet_icon_size,
                com.android.systemui.tv.res.R.dimen.bottom_sheet_icon_size);

        mTitle.setText(R.string.sensor_privacy_start_use_mic_blocked_dialog_title);
        mContent.setText(R.string.sensor_privacy_htt_blocked_dialog_content);
        mIcon.setImageResource(com.android.internal.R.drawable.perm_group_microphone);
        mSecondIcon.setVisibility(View.GONE);

        mPositiveButton.setText(R.string.sensor_privacy_dialog_open_settings);
        mPositiveButton.setOnClickListener(v -> {
            Intent openPrivacySettings = new Intent(ACTION_MANAGE_MICROPHONE_PRIVACY);
            ActivityInfo activityInfo = openPrivacySettings.resolveActivityInfo(getPackageManager(),
                    PackageManager.MATCH_SYSTEM_ONLY);
            if (activityInfo == null) {
                showToastAndFinish(com.android.internal.R.string.noApplications);
            } else {
                startActivity(openPrivacySettings);
                finish();
            }
        });
    }

    private void setIconTint(boolean enableTint) {
        final Resources resources = getResources();

        if (enableTint) {
            final ColorStateList iconTint =
                    resources.getColorStateList(
                            com.android.systemui.tv.res.R.color.bottom_sheet_icon_color,
                            getTheme());
            mIcon.setImageTintList(iconTint);
            mSecondIcon.setImageTintList(iconTint);
        } else {
            mIcon.setImageTintList(null);
            mSecondIcon.setImageTintList(null);
        }

        mIcon.invalidate();
        mSecondIcon.invalidate();
    }

    private void setIconSize(@DimenRes int widthRes, @DimenRes int heightRes) {
        final Resources resources = getResources();
        final int iconWidth = resources.getDimensionPixelSize(widthRes);
        final int iconHeight = resources.getDimensionPixelSize(heightRes);

        mIcon.getLayoutParams().width = iconWidth;
        mIcon.getLayoutParams().height = iconHeight;
        mIcon.invalidate();

        mSecondIcon.getLayoutParams().width = iconWidth;
        mSecondIcon.getLayoutParams().height = iconHeight;
        mSecondIcon.invalidate();
    }

    private boolean isHTTAccessDisabled() {
        String pkg = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        List<String> assistantPkgs = mRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT);
        if (!assistantPkgs.contains(pkg)) {
            return false;
        }

        return (mAppOpsManager.checkOpNoThrow(
                AppOpsManager.OP_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO, UserHandle.myUserId(),
                pkg) != AppOpsManager.MODE_ALLOWED);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        mSensorPrivacyController.addCallback(mSensorPrivacyCallback);
    }

    @Override
    public void onPause() {
        mSensorPrivacyController.removeCallback(mSensorPrivacyCallback);
        super.onPause();
    }

}
