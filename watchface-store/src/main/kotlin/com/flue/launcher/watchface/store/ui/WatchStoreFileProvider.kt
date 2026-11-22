package com.flue.launcher.watchface.store.ui

import androidx.core.content.FileProvider

/** A distinct manifest component keeps store grants separate from HOME's FileProvider. */
class WatchStoreFileProvider : FileProvider()
