/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import androidx.core.net.toUri

class UriUtils {
    companion object {
        fun hasHttpProtocolPrefixed(uri: String): Boolean = uri.startsWith("http://") || uri.startsWith("https://")

        fun extractInstanceInternalFileFileId(url: String): String {
            // https://nc.softblinktech.com/apps/files/?dir=/Engineering&fileid=41
            return url.toUri().getQueryParameter("fileid").toString()
        }

        fun isInstanceInternalFileShareUrl(baseUrl: String, url: String): Boolean {
            // https://nc.softblinktech.com/f/41
            // https://nc.softblinktech.com/index.php/f/41
            return (url.startsWith("$baseUrl/f/") || url.startsWith("$baseUrl/index.php/f/")) &&
                Regex(".*/f/\\d*").matches(url)
        }

        fun isInstanceInternalTalkUrl(baseUrl: String, url: String): Boolean {
            // https://nc.softblinktech.com/call/123456789
            // https://nc.softblinktech.com/call/exsnuuik (alphanumeric tokens)
            return (url.startsWith("$baseUrl/call/") || url.startsWith("$baseUrl/index.php/call/")) &&
                Regex(".*/call/[a-zA-Z0-9]+").matches(url)
        }

        fun extractInstanceInternalFileShareFileId(url: String): String {
            // https://nc.softblinktech.com/f/41
            return url.toUri().lastPathSegment ?: ""
        }

        fun extractRoomTokenFromTalkUrl(url: String): String {
            // https://nc.softblinktech.com/call/exsnuuik
            return url.toUri().lastPathSegment ?: ""
        }

        fun isInstanceInternalFileUrl(baseUrl: String, url: String): Boolean {
            // https://nc.softblinktech.com/apps/files/?dir=/Engineering&fileid=41
            return (
                url.startsWith("$baseUrl/apps/files/") ||
                    url.startsWith("$baseUrl/index.php/apps/files/")
                ) &&
                url.toUri().queryParameterNames.contains("fileid") &&
                Regex(""".*fileid=\d*""").matches(url)
        }

        fun isInstanceInternalFileUrlNew(baseUrl: String, url: String): Boolean {
            // https://nc.softblinktech.com/apps/files/files/41?dir=/
            return url.startsWith("$baseUrl/apps/files/files/") ||
                url.startsWith("$baseUrl/index.php/apps/files/files/")
        }

        fun extractInstanceInternalFileFileIdNew(url: String): String {
            // https://nc.softblinktech.com/apps/files/files/41?dir=/
            return url.toUri().lastPathSegment ?: ""
        }
    }
}
