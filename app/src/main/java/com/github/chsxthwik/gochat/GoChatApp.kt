package com.github.chsxthwik.gochat

import android.app.Application
import com.github.chsxthwik.gochat.data.AgentEngine
import com.github.chsxthwik.gochat.data.ChatDatabase
import com.github.chsxthwik.gochat.data.ChatRepository
import com.github.chsxthwik.gochat.data.Connectivity
import com.github.chsxthwik.gochat.data.GoApi
import com.github.chsxthwik.gochat.data.SettingsStore

class GoChatApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var api: GoApi
        private set
    lateinit var repo: ChatRepository
        private set
    lateinit var connectivity: Connectivity
        private set
    lateinit var agentEngine: AgentEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        api = GoApi()
        repo = ChatRepository(ChatDatabase.get(this))
        connectivity = Connectivity(applicationContext)
        agentEngine = AgentEngine(api, repo)
    }
}
