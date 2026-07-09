package org.itroboc.app

/**
 * Neutral shared camera/analyzer name.
 *
 * The current backing implementation still lives in [AdminEditCameraFrameDecoder]
 * for compatibility with existing call sites. New shared camera/analyzer work
 * should prefer this neutral name.
 */
internal typealias CameraFrameDecoder = AdminEditCameraFrameDecoder
