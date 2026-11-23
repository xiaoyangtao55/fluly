package com.flue.launcher.ui.settings

/** Picker ids are lowercase; enum declarations need not be uppercase. Unknown ids are ignored. */
internal inline fun <reified T : Enum<T>> settingsEnumOption(optionId: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(optionId, ignoreCase = true) }
