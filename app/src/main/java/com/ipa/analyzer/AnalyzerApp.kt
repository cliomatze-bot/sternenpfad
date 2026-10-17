package com.ipa.analyzer

import android.app.Application
import com.ipa.analyzer.data.GraphApiClient
import com.ipa.analyzer.data.SettingsStore
import com.ipa.analyzer.data.SnapshotRepository

/** Einfacher manueller Abhängigkeits-Container. */
class AnalyzerApp : Application() {
    lateinit var repository: SnapshotRepository
        private set
    lateinit var settings: SettingsStore
        private set
    val api = GraphApiClient()

    override fun onCreate() {
        super.onCreate()
        repository = SnapshotRepository(filesDir)
        settings = SettingsStore(this)
    }
}
