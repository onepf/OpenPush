/*
 * Copyright 2012-2014 One Platform Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onepf.opfpush;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.onepf.opfpush.model.RecoverablePushError;
import org.onepf.opfutils.OPFChecks;
import org.onepf.opfutils.OPFLog;
import org.onepf.opfutils.OPFUtils;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;
import static android.Manifest.permission.WAKE_LOCK;
import static org.onepf.opfpush.model.RecoverablePushError.Type.REGISTERING_PERFORMING;
import static org.onepf.opfpush.model.RecoverablePushError.Type.UNREGISTERING_PERFORMING;

/**
 * Implements the common functionality of the {@link org.onepf.opfpush.PushProvider} interface.
 * This class in intended to simplify the {@code PushProvider} implementation.
 *
 * @author Kirill Rozov
 * @author Roman Savin
 * @since 05.09.14
 */
public abstract class BasePushProvider implements PushProvider {

    @NonNull
    private final Context appContext;

    @NonNull
    private final String name;

    @NonNull
    private final String hostAppPackage;

    /**
     * Creates a push provider.
     *
     * @param context        The {@link android.content.Context} instance.
     * @param name           The name of the provider.
     * @param hostAppPackage The package of the application that handle push messages from the server
     *                       and deliver it to the user application.
     */
    protected BasePushProvider(@NonNull final Context context,
                               @NonNull final String name,
                               @NonNull final String hostAppPackage) {
        this.appContext = context.getApplicationContext();
        this.name = name;
        this.hostAppPackage = hostAppPackage;
    }

    @Override
    public boolean isAvailable() {
        return OPFUtils.isInstalled(appContext, hostAppPackage);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BasePushProvider that = (BasePushProvider) o;
        return name.equals(that.name);
    }

    @Override
    public void register() {
        if (!isUnregistrationPerforming()) {
            OPFPush.getHelper().getSettings().saveRegisteringProvider(name);
        } else {
            OPFLog.i("Unregistering is performing.");
            sendUnregisteringPerformingError();
        }
    }

    @Override
    public void unregister() {
        if (!isRegistrationPerforming()) {
            OPFPush.getHelper().getSettings().saveUnregisteringProvider(name);
        } else {
            OPFLog.i("Registration is performing.");
            sendRegistrationPerformingError();
        }
    }

    @Override
    public void checkManifest() {
        OPFChecks.checkPermission(appContext, INTERNET);
        OPFChecks.checkPermission(appContext, RECEIVE_BOOT_COMPLETED);
        OPFChecks.checkPermission(appContext, WAKE_LOCK);
        OPFChecks.checkPermission(appContext, ACCESS_NETWORK_STATE);

        OPFChecks.checkReceiver(appContext, BootCompleteReceiver.class.getName(),
                new Intent(Intent.ACTION_BOOT_COMPLETED));
        OPFChecks.checkReceiver(appContext, RetryBroadcastReceiver.class.getName(),
                new Intent(appContext, RetryBroadcastReceiver.class));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getHostAppPackage() {
        return hostAppPackage;
    }

    @Override
    public String toString() {
        return name + "(hostAppPackage='" + hostAppPackage + ')';
    }

    /**
     * Returns {@code true} if the provider performs the registration operation at the moment.
     *
     * @return {@code true} if the provider performs the registration operation at the moment.
     */
    protected boolean isRegistrationPerforming() {
        return OPFPush.getHelper().getSettings().isProviderRegistrationPerforming(name);
    }

    /**
     * Returns {@code true} if the provider performs the unregistration operation at the moment.
     *
     * @return {@code true} if the provider performs the unregistration operation at the moment.
     */
    protected boolean isUnregistrationPerforming() {
        return OPFPush.getHelper().getSettings().isProviderUnregistrationPerforming(name);
    }

    private void sendRegistrationPerformingError() {
        OPFPush.getHelper().getReceivedMessageHandler().onUnregistrationError(
                name,
                new RecoverablePushError(REGISTERING_PERFORMING, name, REGISTERING_PERFORMING.name())
        );
    }

    private void sendUnregisteringPerformingError() {
        OPFPush.getHelper().getReceivedMessageHandler().onRegistrationError(
                name,
                new RecoverablePushError(UNREGISTERING_PERFORMING, name, UNREGISTERING_PERFORMING.name())
        );
    }

    /**
     * @return The instance of the application context.
     */
    @NonNull
    protected Context getContext() {
        return appContext;
    }
}
