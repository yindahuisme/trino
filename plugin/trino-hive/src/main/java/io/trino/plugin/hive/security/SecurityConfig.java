/*
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
package io.trino.plugin.hive.security;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.NotNull;

import static io.trino.plugin.hive.security.HiveSecurityModule.LEGACY;

public class SecurityConfig
{
    private String securitySystem = LEGACY;

    @NotNull
    public String getSecuritySystem()
    {
        return securitySystem;
    }

    @Config("hive.security")
    public SecurityConfig setSecuritySystem(String securitySystem)
    {
        this.securitySystem = securitySystem;
        return this;
    }
}
