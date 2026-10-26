package com.luckyzyx.luckytool.data

import java.io.Serializable

data class DisplayMode(
    var id: Int,
    var width: Int? = null,
    var height: Int? = null,
    var xDpi: Float? = null,
    var yDpi: Float? = null,
    var refreshRate: Float? = null,
) : Serializable {

    constructor() : this(-1)

}