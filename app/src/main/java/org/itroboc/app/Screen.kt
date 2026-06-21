package org.itroboc.app

sealed class Screen {
    object MainMenu : Screen()
    object TdActions : Screen()
    object AdminActions : Screen()
    object MockActions : Screen()
    data class BoardScan(val boardNumber: Int) : Screen()
}
