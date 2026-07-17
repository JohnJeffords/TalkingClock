# R8/ProGuard rules for release builds.
#
# The app is pure Kotlin/Compose with no reflection, no serialization
# frameworks, and no JNI, so the defaults in proguard-android-optimize.txt
# cover everything. Add keep-rules here only if a future dependency needs
# them — and document why, so the next reader knows the rule is load-bearing.
