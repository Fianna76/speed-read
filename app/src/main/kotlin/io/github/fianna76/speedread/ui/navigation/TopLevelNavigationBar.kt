package io.github.fianna76.speedread.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.fianna76.speedread.R

/** The app's main sections, switched with the bottom navigation bar. */
enum class TopLevelDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    LIBRARY(R.string.nav_library, R.drawable.ic_menu_book),
    NEWS(R.string.nav_news, R.drawable.ic_newspaper),
}

@Composable
fun TopLevelNavigationBar(current: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}
