package com.anaglych.squintboyadvance.shared.protocol

object WearMessageConstants {
    // Channel paths (large data via ChannelClient)
    const val PATH_ROM_TRANSFER = "/rom/transfer"
    const val PATH_SAVE_PULL = "/save/pull"
    const val PATH_SAVE_PUSH = "/save/push"

    // Save archive sync (opt-in automatic SRAM archival, managed from phone).
    //
    // Channel, watch → phone: outbox drain. Lock-step per entry:
    //   watch writes JSON line SaveArchiveEntryMeta + '\n', 8-byte big-endian length, blob,
    //   then reads one ack line: "OK" | "REJECT <reason>" | "RETRY <reason>".
    //   OK → entry durably archived, watch deletes it. REJECT → poison, watch deletes it.
    //   RETRY → transient phone-side failure, watch keeps entry and aborts the drain.
    //   After the last entry the watch writes the literal line "END".
    const val PATH_SAVE_ARCHIVE_PUSH = "/save/archive/push"

    // Channel, phone → watch: validated save restore (v2 of PATH_SAVE_PUSH).
    // Phone writes lines: "romId/fileName", sizeBytes (decimal), sha256 (lowercase hex),
    // then raw bytes. Watch verifies size + hash, atomically installs, replies
    // "OK\n" or "ERR <msg>\n". Phones fall back to PATH_SAVE_PUSH on failure/timeout.
    const val PATH_SAVE_PUSH_V2 = "/save/push_v2"

    // Message, phone → watch: JSON-encoded SaveSyncConfig. Phone is authoritative.
    const val PATH_SAVE_SYNC_CONFIG = "/save/sync/config"

    // Message, watch → phone: empty payload; phone replies on PATH_SAVE_SYNC_CONFIG.
    const val PATH_SAVE_SYNC_CONFIG_REQUEST = "/save/sync/config/request"

    // Message, phone → watch: empty payload; watch starts an outbox drain if enabled.
    const val PATH_SAVE_ARCHIVE_DRAIN = "/save/archive/drain"

    // Message paths (small data via MessageClient)
    const val PATH_ROM_LIST_REQUEST = "/rom/list/request"
    const val PATH_ROM_LIST_RESPONSE = "/rom/list/response"
    const val PATH_ROM_DELETE = "/rom/delete"
    const val PATH_SAVE_LIST_REQUEST = "/save/list/request"
    const val PATH_SAVE_LIST_RESPONSE = "/save/list/response"
    const val PATH_SETTINGS_REQUEST = "/settings/request"
    const val PATH_SETTINGS_RESPONSE = "/settings/response"
    const val PATH_SETTINGS_SYNC = "/settings/sync"

    // Screen info: phone requests watch screen dimensions for dynamic scale range.
    // Response payload: screenWidthPx as UTF-8 int string (e.g. "454").
    const val PATH_SCREEN_INFO_REQUEST = "/screen/info/request"
    const val PATH_SCREEN_INFO_RESPONSE = "/screen/info/response"

    // Clears save-state stack (.ss0–.ss4) and SRAM backup stack (.sav.0–.sav.4) for a ROM.
    // Payload: romId (UTF-8). Used after uploading a new save from phone to watch.
    const val PATH_SAVE_CLEAR_STACKS = "/save/clear_stacks"

    // Sent from watch to phone: asks the companion app to open its ROM file picker.
    const val PATH_OPEN_ROM_PICKER = "/rom/picker/open"

    // Sent from watch to phone: reports the result of a ROM transfer.
    // Payload: JSON-encoded TransferResult.
    const val PATH_ROM_TRANSFER_RESULT = "/rom/transfer/result"

    // Sent from phone to watch: sets a custom display name for a ROM.
    // Payload: "$romId\n$newName" (UTF-8). Empty newName clears the override.
    const val PATH_ROM_RENAME = "/rom/rename"

    // Ping/pong: watch sends ping, phone replies with pong.
    // Used to detect companion app presence (bypasses ReVanced capability interference).
    const val PATH_PHONE_PING = "/phone/ping"
    const val PATH_PHONE_PONG = "/phone/pong"

    // Ping/pong: phone sends ping, watch replies with pong.
    // Used by the companion app to confirm the watch app is installed and reachable.
    const val PATH_WATCH_PING = "/watch/ping"
    const val PATH_WATCH_PONG = "/watch/pong"

    // Watch → Phone: asks phone to launch the IAP purchase flow.
    const val PATH_PURCHASE_ON_PHONE = "/purchase/phone"

    // Watch → Phone: asks phone to show the Play in-app review sheet.
    const val PATH_TRIGGER_REVIEW = "/review/trigger"

    // Entitlement sync: phone is authoritative (owns BillingClient).
    // Phone pushes state to watch after purchase or on connect.
    const val PATH_ENTITLEMENT_PUSH = "/entitlement/push"
    const val PATH_ENTITLEMENT_REQUEST = "/entitlement/request"
    const val PATH_ENTITLEMENT_RESPONSE = "/entitlement/response"

    // Capability names declared in res/values/wear.xml on each side.
    const val CAPABILITY_PHONE_APP = "squintboy_phone_app"
    const val CAPABILITY_WATCH_APP = "squintboy_watch_app"

    // Play Store URI used by RemoteActivityHelper to open the store on the other device.
    const val PLAY_STORE_URI = "market://details?id=com.anaglych.squintboyadvance"
}
