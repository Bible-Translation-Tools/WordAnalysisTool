package org.bibletranslationtools.wat.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write

actual suspend fun saveFile(bytes: ByteArray, filename: String, extension: String) {
    FileKit.openFileSaver(
        suggestedName = filename,
        defaultExtension = extension,
        directory = null,
        dialogSettings = FileKitDialogSettings.createDefault()
    )?.write(bytes)
}