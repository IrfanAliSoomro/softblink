/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.threads

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ThreadAttendee(
    @JsonField(name = ["notificationLevel"])
    var notificationLevel: Int = 0,

    @JsonField(name = ["lastReadMessage"])
    var lastReadMessage: Int = 0,

    @JsonField(name = ["lastMentionMessage"])
    var lastMentionMessage: Int = 0,

    @JsonField(name = ["lastMentionDirect"])
    var lastMentionDirect: Int = 0,

    @JsonField(name = ["readPrivacy"])
    var readPrivacy: Int = 0
) : Parcelable
