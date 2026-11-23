package com.tareqsoft.market

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tareqsoft.market.ui.MandatoryUpdateGate
import com.tareqsoft.market.ui.MarketTheme
import com.tareqsoft.market.ui.screens.CategoriesScreen
import com.tareqsoft.market.ui.screens.CollectionsScreen
import com.tareqsoft.market.ui.screens.CustomerDetailScreen
import com.tareqsoft.market.ui.screens.CustomersScreen
import com.tareqsoft.market.ui.screens.HomeScreen
import com.tareqsoft.market.ui.screens.PosScreen
import com.tareqsoft.market.ui.screens.ProductEditScreen
import com.tareqsoft.market.ui.screens.ProductsScreen
import com.tareqsoft.market.ui.screens.ReportsScreen
import com.tareqsoft.market.ui.screens.ReturnScreen
import com.tareqsoft.market.ui.screens.SaleDetailScreen
import com.tareqsoft.market.ui.screens.SalesScreen
import com.tareqsoft.market.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val PRODUCTS_PATTERN = "products?category={category}"
    const val CATEGORIES = "categories"
    const val PRODUCT = "product/{id}"
    const val POS = "pos"
    const val SALES = "sales"
    const val SALE = "sale/{id}"
    const val RETURN = "return/{id}"
    const val COLLECTIONS = "collections"
    const val CUSTOMERS = "customers"
    const val CUSTOMER = "customer/{id}"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    fun product(id: String) = "product/$id"
    fun productsIn(category: String) = "products?category=${android.net.Uri.encode(category)}"
    fun sale(id: String) = "sale/$id"
    fun saleReturn(saleId: String) = "return/$saleId"
    fun customer(id: String) = "customer/$id"
    const val NEW = "new"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars on every Android version (Android 15 forces it anyway)
        // and let each screen pad itself, so no button ends up under the navigation bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val container = (application as MarketApp).container
        setContent {
            MarketTheme(container) {
                val nav = rememberNavController()
                val back: () -> Unit = { nav.popBackStack() }

                // Each time the app opens: look for a new version and download it
                // in the background, so the user only has to tap "Install".
                LaunchedEffect(Unit) { container.updater.checkAndDownload() }
                // A mandatory update covers the whole app until it is installed.
                MandatoryUpdateGate()

                NavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(navigate = { nav.navigate(it) }) }
                    composable(
                        Routes.PRODUCTS_PATTERN,
                        listOf(navArgument("category") { type = NavType.StringType; defaultValue = "" }),
                    ) {
                        ProductsScreen(
                            onBack = back,
                            onOpen = { nav.navigate(Routes.product(it)) },
                            onNew = { nav.navigate(Routes.product(Routes.NEW)) },
                            onDepartments = { nav.navigate(Routes.CATEGORIES) },
                            initialCategory = it.arguments?.getString("category") ?: "",
                        )
                    }
                    composable(Routes.CATEGORIES) {
                        CategoriesScreen(onBack = back, onOpenCategory = { c -> nav.navigate(Routes.productsIn(c)) })
                    }
                    composable(Routes.PRODUCT, listOf(navArgument("id") { type = NavType.StringType })) {
                        ProductEditScreen(productId = it.arguments?.getString("id") ?: Routes.NEW, onBack = back)
                    }
                    composable(Routes.POS) {
                        PosScreen(onBack = back, onOpenSale = { id ->
                            nav.navigate(Routes.sale(id))
                        })
                    }
                    composable(Routes.SALES) {
                        SalesScreen(onBack = back, onOpen = { nav.navigate(Routes.sale(it)) })
                    }
                    composable(Routes.SALE, listOf(navArgument("id") { type = NavType.StringType })) {
                        SaleDetailScreen(
                            saleId = it.arguments?.getString("id")!!,
                            onBack = back,
                            onReturn = { id -> nav.navigate(Routes.saleReturn(id)) },
                        )
                    }
                    composable(Routes.RETURN, listOf(navArgument("id") { type = NavType.StringType })) {
                        ReturnScreen(saleId = it.arguments?.getString("id")!!, onBack = back)
                    }
                    composable(Routes.COLLECTIONS) {
                        CollectionsScreen(onBack = back, onOpenSale = { nav.navigate(Routes.sale(it)) })
                    }
                    composable(Routes.CUSTOMERS) {
                        CustomersScreen(onBack = back, onOpen = { nav.navigate(Routes.customer(it)) })
                    }
                    composable(Routes.CUSTOMER, listOf(navArgument("id") { type = NavType.StringType })) {
                        CustomerDetailScreen(
                            customerId = it.arguments?.getString("id")!!,
                            onBack = back,
                            onOpenSale = { id -> nav.navigate(Routes.sale(id)) },
                        )
                    }
                    composable(Routes.REPORTS) { ReportsScreen(onBack = back) }
                    composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
                }
            }
        }
    }
}
