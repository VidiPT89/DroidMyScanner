package dev.ividi.myscanner.ui

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val VIEWER = "viewer/{documentId}"
    const val EDITOR = "editor/{documentId}/{pageId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun viewer(documentId: String) = "viewer/$documentId"
    fun editor(documentId: String, pageId: String) = "editor/$documentId/$pageId"
}
