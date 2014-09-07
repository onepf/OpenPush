/*******************************************************************************
 * Copyright 2014 One Platform Foundation
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 ******************************************************************************/

package org.onepf.openpush;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import junit.framework.Assert;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.greenrobot.event.EventBus;

public class OpenPushHelper {

    private static final String KEY_LAST_PROVIDER_NAME = "last_provider_name";
    private static final String KEY_INIT_STATUS = "init_status";

    public static final int INIT_NOT_STARTED = 0;
    public static final int INIT_IN_PROGRESS = 1;
    public static final int INIT_SUCCESS = 2;
    public static final int INIT_ERROR = 3;

    private static Handler sHandler = new Handler(Looper.getMainLooper());

    private int mInitStatus;
    private Options mOptions;
    private final Context mAppContext;
    private PushProvider mCurrentProvider;
    private final String mId;
    private int mRetryNumber = 0;

    private static OpenPushListener sListener;

    private final SharedPreferences mPreferences;

    public OpenPushHelper(@NotNull Context context, @NotNull String id) {
        mAppContext = context.getApplicationContext();
        mId = id;
        sListener = new BroadcastOpenPushListener(mAppContext);
        mPreferences = context.getSharedPreferences(
                String.format("org.onepf.openpush.prefs_%s", id),
                Context.MODE_PRIVATE
        );
        mInitStatus = mPreferences.getInt(KEY_INIT_STATUS, INIT_NOT_STARTED);

        PushProvider provider = getLastProvider();
        if (provider != null && provider.isAvailable()
                && mInitStatus == INIT_SUCCESS) {
            mCurrentProvider = provider;
        }
    }

    public String getId() {
        return mId;
    }

    public static OpenPushListener getListener() {
        return sListener;
    }

    public void setListener(OpenPushListener l) {
        if (l == null) {
            sListener = new BroadcastOpenPushListener(mAppContext);
        } else {
            sListener = l;
        }
    }

    public synchronized void register(@NotNull Options options) {
        if (mInitStatus == INIT_NOT_STARTED || mInitStatus == INIT_ERROR) {
            mInitStatus = INIT_IN_PROGRESS;
            mOptions = options;

            PushProvider provider = getNextCandidate(null);
            if (provider != null) {
                EventBus.getDefault().register(this);
                provider.register();
            } else {
                sListener.onNoAvailableProvider();
                mInitStatus = INIT_ERROR;
            }
        } else {
            throw new IllegalStateException("Attempt to register twice!");
        }
    }

    public void unregister() {
        if (mInitStatus == INIT_SUCCESS) {
            mCurrentProvider.unregister();
            EventBus.getDefault().unregister(this);
            mCurrentProvider = null;
            mInitStatus = INIT_NOT_STARTED;
            saveProvider(null);
            mPreferences.edit().remove(KEY_INIT_STATUS).apply();
        } else {
            throw new IllegalStateException("Attempt to unregister not initialised!");
        }
    }

    @Nullable
    private PushProvider getNextCandidate(@Nullable PushProvider lastProvider) {
        int i = 0;
        if (lastProvider != null) {
            int lastCandidateIndex = mOptions.getProviders().indexOf(lastProvider);
            if (lastCandidateIndex != -1) {
                i = lastCandidateIndex + 1;
            }
        }
        for (int cnt = mOptions.getProviders().size(); i < cnt; ++i) {
            PushProvider candidate = mOptions.getProviders().get(i);
            if (candidate.isAvailable()) {
                return candidate;
            }
        }
        return null;
    }

    private void onRegistrationResult(@NotNull final RegistrationResult result) {
        final PushProvider provider = getProviderByName(result.getProviderName());
        Assert.assertNotNull(provider);

        if (result.isSuccess()) {
            mInitStatus = INIT_SUCCESS;
            mPreferences.edit().putInt(KEY_INIT_STATUS, INIT_SUCCESS).apply();
            mRetryNumber = 0;
            mCurrentProvider = provider;
            saveProvider(mCurrentProvider);
            sListener.onRegistered(result.getProviderName(), result.getRegistrationId());
        } else {
            if (result.isRecoverableError() && mOptions.getBackoff() != null
                    && mRetryNumber < mOptions.getBackoff().tryCount()) {
                sHandler.postDelayed(new RetryRegistrationRunnable(provider),
                        mOptions.getBackoff().getDelay(mRetryNumber));
                mRetryNumber++;
            } else {
                sListener.onRegistrationError(provider.getName(), result.getErrorCode());

                mRetryNumber = 0;
                PushProvider nextProvider = getNextCandidate(provider);
                if (nextProvider != null) {
                    nextProvider.register();
                } else {
                    mInitStatus = INIT_ERROR;
                    sListener.onNoAvailableProvider();
                }
            }
        }
    }

    @Nullable
    public String getCurrentProviderName() {
        return mCurrentProvider == null ? null : mCurrentProvider.getName();
    }

    @Nullable
    public String getCurrentProviderRegistrationId() {
        return mCurrentProvider == null ? null : mCurrentProvider.getRegistrationId();
    }

    @Nullable
    private PushProvider getProviderByName(@NotNull String providerName) {
        for (PushProvider provider : mOptions.getProviders()) {
            if (providerName.equals(provider.getName())) {
                return provider;
            }
        }
        return null;
    }

    @Nullable
    private PushProvider getLastProvider() {
        final String storedProviderName = mPreferences.getString(KEY_LAST_PROVIDER_NAME, null);
        if (storedProviderName != null) {
            for (PushProvider provider : mOptions.getProviders()) {
                if (storedProviderName.equals(provider.getName())) {
                    return provider;
                }
            }
        }
        return null;
    }

    private void saveProvider(@Nullable PushProvider provider) {
        if (provider == null) {
            mPreferences.edit()
                    .remove(KEY_LAST_PROVIDER_NAME)
                    .apply();
        } else {
            mPreferences.edit()
                    .putString(KEY_LAST_PROVIDER_NAME, provider.getName())
                    .apply();
        }
    }

    @MagicConstant(intValues = {INIT_ERROR, INIT_IN_PROGRESS, INIT_NOT_STARTED, INIT_SUCCESS})
    public int getInitStatus() {
        return mInitStatus;
    }

    private static class RetryRegistrationRunnable implements Runnable {
        private final PushProvider mProvider;

        public RetryRegistrationRunnable(@NotNull PushProvider provider) {
            mProvider = provider;
        }

        @Override
        public void run() {
            mProvider.register();
        }
    }
}
