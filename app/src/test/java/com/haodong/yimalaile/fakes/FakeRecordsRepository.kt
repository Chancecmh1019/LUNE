@file:Suppress("unused")
package com.lune.app.fakes

// Re-export from commonMain for backwards compatibility.
// All test data and FakeRecordsRepository are now in:
// commonMain/kotlin/com/haodong/lune/fakes/ScreenshotTestData.kt

/**
 * Alias for the shared [createScreenshotTestData] function.
 */
fun createBeautifulTestData() = createScreenshotTestData()
