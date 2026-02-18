package com.example.squintboyadvance.shared.protocol

object WearMessageConstants {
    // Channel paths (large data via ChannelClient)
    const val PATH_ROM_TRANSFER = "/rom/transfer"
    const val PATH_SAVE_PULL = "/save/pull"
    const val PATH_SAVE_PUSH = "/save/push"

    // Message paths (small data via MessageClient)
    const val PATH_ROM_LIST_REQUEST = "/rom/list/request"
    const val PATH_ROM_LIST_RESPONSE = "/rom/list/response"
    const val PATH_ROM_DELETE = "/rom/delete"
    const val PATH_SAVE_LIST_REQUEST = "/save/list/request"
    const val PATH_SAVE_LIST_RESPONSE = "/save/list/response"
    const val PATH_SETTINGS_REQUEST = "/settings/request"
    const val PATH_SETTINGS_RESPONSE = "/settings/response"
    const val PATH_SETTINGS_SYNC = "/settings/sync"
}
