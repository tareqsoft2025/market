package com.tareqsoft.market

import android.app.Application
import com.tareqsoft.market.data.BackupManager
import com.tareqsoft.market.data.MarketDatabase
import com.tareqsoft.market.data.MarketRepository
import com.tareqsoft.market.update.Updater
import com.tareqsoft.market.util.Settings

/** Simple manual dependency container shared by all screens. */
class AppContainer(app: Application) {
    val db: MarketDatabase = MarketDatabase.build(app)
    val repo = MarketRepository(db)
    val settings = Settings(app)
    val updater = Updater(app)
    val backup = BackupManager(app, db)
}

class MarketApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
