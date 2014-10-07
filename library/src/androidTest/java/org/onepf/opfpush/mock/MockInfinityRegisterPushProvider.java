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

package org.onepf.opfpush.mock;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Created by krozov on 24.09.14.
 */
public class MockInfinityRegisterPushProvider extends MockPushProvider {
    public MockInfinityRegisterPushProvider(@NonNull Context context) {
        super(context);
    }

    public MockInfinityRegisterPushProvider(@NonNull Context context, String name) {
        super(context, name);
    }

    public MockInfinityRegisterPushProvider(@NonNull Context context, @NonNull String name, boolean available) {
        super(context, name, available);
    }

    public MockInfinityRegisterPushProvider(@NonNull Context context, @NonNull String name, @NonNull String hotAppPackage) {
        super(context, name, hotAppPackage);
    }

    public MockInfinityRegisterPushProvider(@NonNull Context context, @NonNull String name, boolean available, @NonNull String hotAppPackage) {
        super(context, name, available, hotAppPackage);
    }

    @Override
    public void register() {
    }
}