/* 
 * Copyright 2023 Telenav.
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
package com.telenav.smithy.vertx.bunyan.logging;

import com.mastfrog.settings.Settings;
import javax.inject.Inject;

/**
 * Default implementation of LoggingProbeConfiguration, which utilizes the
 * registry of primary settings generated by annotation processors and read from
 * all JARs on the classpath to determine what configuration keys are relevant
 * to log.
 *
 * @author Tim Boudreau
 */
final class DefaultLoggingProbeConfiguration implements LoggingProbeConfiguration {

    private static final String SETTINGS_KEY_LOGGED_SETTINGS = "logsettings";
    private final Settings settings;

    @Inject
    DefaultLoggingProbeConfiguration(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String[] configurationKeysToLog() {
        String[] toLog = settings.getStringArray(SETTINGS_KEY_LOGGED_SETTINGS);
        if (toLog != null && toLog.length > 0) {
            return toLog;
        }
        return LoggingProbeConfiguration.super.configurationKeysToLog();
    }

}
