package it.unibo.collektive.navigation

sealed class Pages(val route: String) {
    data object Chat: Pages("ChatPage")
    data object Home: Pages("HomePage")
}
