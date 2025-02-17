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

package com.android.systemui.tv.statusbar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.CoreStartable;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.KeyboardShortcuts;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * Status bar implementation for "large screen" products that mostly present no on-screen nav.
 * Serves as a collection of UI components, rather than showing its own UI.
 */
@SysUISingleton
public class TvStatusBar implements CoreStartable, CommandQueue.Callbacks {

    private static final String ACTION_SHOW_PIP_MENU =
            "com.android.wm.shell.pip.tv.notification.action.SHOW_PIP_MENU";
    private static final String SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF";

    private final Context mContext;
    private final CommandQueue mCommandQueue;
    private final Lazy<AssistManager> mAssistManagerLazy;

    @Inject
    public TvStatusBar(Context context, CommandQueue commandQueue,
            Lazy<AssistManager> assistManagerLazy) {
        mContext = context;
        mCommandQueue = commandQueue;
        mAssistManagerLazy = assistManagerLazy;
    }

    @Override
    public void start() {
        final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mCommandQueue.addCallback(this);
        try {
            barService.registerStatusBar(mCommandQueue);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }
    }

    @Override
    public void startAssist(Bundle args) {
        mAssistManagerLazy.get().startAssist(args);
    }

    @Override
    public void showPictureInPictureMenu() {
        mContext.sendBroadcast(
                new Intent(ACTION_SHOW_PIP_MENU).setPackage(mContext.getPackageName()),
                SYSTEMUI_PERMISSION);
    }

    @Override
    public void toggleKeyboardShortcutsMenu(int deviceId) {
        KeyboardShortcuts.show(mContext, deviceId);
    }
}
