package com.swadeshiscanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swadeshiscanner.app.R
import com.swadeshiscanner.app.ui.theme.CSGreen
import com.swadeshiscanner.app.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: Int) {
    object Home : Screen("home", "Home", R.drawable.ic_home)
    object Files : Screen("files", "Files", R.drawable.ic_folder)
    object Tools : Screen("tools", "Tools", R.drawable.ic_settings)
    object Me : Screen("me", "Me", R.drawable.ic_person)
}

@Composable
fun MainScreen(
    onScanClick: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Fix: Add background to root Box
    ) {
        // Main Content - Full Screen Edge-to-Edge
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background) // Fix: Prevent white flash
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = { androidx.compose.animation.fadeIn() },
                exitTransition = { androidx.compose.animation.fadeOut() }
            ) { 
                HomeScreen(
                    onNavigateToTools = {
                        navController.navigate(Screen.Tools.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onNavigateToFiles = {
                        navController.navigate(Screen.Files.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                ) 
            }
            composable(
                route = Screen.Files.route,
                enterTransition = { androidx.compose.animation.fadeIn() },
                exitTransition = { androidx.compose.animation.fadeOut() }
            ) { FilesScreen() }
            composable(
                route = Screen.Tools.route,
                enterTransition = { androidx.compose.animation.fadeIn() },
                exitTransition = { androidx.compose.animation.fadeOut() }
            ) { ToolsScreen() }
            composable(
                route = Screen.Me.route,
                enterTransition = { androidx.compose.animation.fadeIn() },
                exitTransition = { androidx.compose.animation.fadeOut() }
            ) { MeScreen() }
        }

        // Floating FAB (Camera) - Bottom Right, above Nav Bar
        FloatingActionButton(
            onClick = onScanClick,
            shape = CircleShape,
            containerColor = CSGreen,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 120.dp)
                .size(60.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = CSGreen,
                    spotColor = CSGreen
                )
                .border(
                    width = 1.5.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.White,
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = "Scan",
                modifier = Modifier.size(32.dp)
            )
        }

        // Floating Navigation Bar - Capsule Style
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp) // Lowered
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(72.dp) // Refined height
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = if (isDark) Color.Black else Color.Gray,
                    spotColor = if (isDark) Color.Black else Color.Gray
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(36.dp)
                ),
            shape = RoundedCornerShape(36.dp),
            color = if (isDark) Color(0xFF222529) else Color(0xFFF1F3F5),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(Screen.Home, Screen.Files, Screen.Tools, Screen.Me).forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val contentColor = if (isSelected) CSGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(38.dp))
                            .clickable {
                                if (!isSelected) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = screen.icon),
                                contentDescription = screen.title,
                                tint = contentColor,
                                modifier = Modifier.size(if (isSelected) 28.dp else 24.dp)
                            )
                            Text(
                                text = screen.title,
                                fontSize = 9.sp,
                                color = contentColor,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}
