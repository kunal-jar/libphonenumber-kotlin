/*
 * Copyright (C) 2022 The Libphonenumber Authors
 * Copyright (C) 2022 Michael Rozumyanskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.michaelrocks.libphonenumber.kotlin.metadata.source

/**
 * [PhoneMetadataFileNameProvider] implementation which appends key as a suffix to the
 * predefined metadata file name base.
 */
class MultiFileModeFileNameProvider(phoneMetadataFileNameBase: String) : PhoneMetadataFileNameProvider {
    private val phoneMetadataFileNamePrefix: String

    init {
        phoneMetadataFileNamePrefix = phoneMetadataFileNameBase + "_"
    }

    override fun getFor(key: Any): String {
        val keyAsString = key.toString()
        require(ALPHANUMERIC.matches(keyAsString)) { "Invalid key: $keyAsString" }
        return phoneMetadataFileNamePrefix + key
    }

    companion object {
        private val ALPHANUMERIC = Regex("^[\\p{L}\\p{N}]+$")
    }
}
