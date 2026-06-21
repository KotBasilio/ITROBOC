package org.itroboc.app

sealed class Screen {
    object MainMenu : Screen()
    object TdActions : Screen()
    object AdminActions : Screen()
    object MockActions : Screen()
}
