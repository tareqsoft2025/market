package com.tareqsoft.market

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tareqsoft.market.ui.MarketTheme
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
    fun sale(id: String) = "sale/$id"
    fun saleReturn(saleId: String) = "return/$saleId"
    fun customer(id: String) = "customer/$id"
    const val NEW = "new"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MarketApp).container
        setContent {
            MarketTheme(container) {
                val nav = rememberNavController()
                val back: () -> Unit = { nav.popBackStack() }

                // Look for a new version once each time the app opens.
                LaunchedEffect(Unit) { container.updater.check(silent = true) }

                NavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(navigate = { nav.navigate(it) }) }
                    composable(Routes.PRODUCTS) {
                        ProductsScreen(
                            onBack = back,
                            onOpen = { nav.navigate(Routes.product(it)) },
                            onNew = { nav.navigate(Routes.product(Routes.NEW)) },
                        )
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
