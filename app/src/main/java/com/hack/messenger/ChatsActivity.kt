package com.hack.messenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class ChatsActivity : AppCompatActivity(), NetworkManager.Listener {

    private lateinit var listView: ListView
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private val chatIds   = mutableListOf("general")
    private val chatNames = mutableListOf("// ОБЩИЙ ЧАТ //")
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        HistoryManager.init(this)
        NetworkManager.listener = this

        listView   = findViewById(R.id.listChats)
        statusDot  = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)

        adapter = ArrayAdapter(this, R.layout.item_chat, R.id.chatName, chatNames)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, pos, _ ->
            openChat(chatIds[pos], chatNames[pos])
        }

        listView.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos > 0) showDeleteChat(pos) else true
            true
        }

        findViewById<View>(R.id.btnNewChat).setOnClickListener { showNewChat() }
        findViewById<View>(R.id.btnNetwork).setOnClickListener { showNetworkDialog() }
        findViewById<View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        updateStatus()
    }

    private fun openChat(chatId: String, title: String) {
        Intent(this, ChatActivity::class.java).also {
            it.putExtra("chat_id", chatId)
            it.putExtra("chat_title", title)
            startActivity(it)
        }
    }

    private fun showNewChat() {
        val edit = EditText(this).apply {
            hint = "Название чата"
            setTextColor(resources.getColor(R.color.green, theme))
            setHintTextColor(resources.getColor(R.color.ddg, theme))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(40,30,40,30)
        }
        AlertDialog.Builder(this)
            .setTitle("// НОВЫЙ ЧАТ //")
            .setView(edit)
            .setPositiveButton("СОЗДАТЬ") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    val id = name.lowercase().replace(" ","_")
                    chatIds.add(id)
                    chatNames.add("// ${name.uppercase()} //")
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun showDeleteChat(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle("Удалить чат?")
            .setPositiveButton("УДАЛИТЬ") { _, _ ->
                chatIds.removeAt(pos)
                chatNames.removeAt(pos)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun showNetworkDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_network, null)
        val ipText    = view.findViewById<TextView>(R.id.myIpText)
        val portEdit  = view.findViewById<EditText>(R.id.editPort)
        val keyEdit   = view.findViewById<EditText>(R.id.editKey)
        val hostEdit  = view.findViewById<EditText>(R.id.editHost)
        val portEdit2 = view.findViewById<EditText>(R.id.editPort2)
        val keyEdit2  = view.findViewById<EditText>(R.id.editKey2)
        val btnSrv    = view.findViewById<Button>(R.id.btnStartServer)
        val btnCli    = view.findViewById<Button>(R.id.btnConnect)
        val logText   = view.findViewById<TextView>(R.id.netLog)

        ipText.text = "МОЙ IP: ${NetworkManager.myIp()}:${NetworkManager.DEFAULT_PORT}"

        if (NetworkManager.isServerRunning()) btnSrv.text = "■ СТОП"
        if (NetworkManager.isConnected())     btnCli.text = "■ ОТКЛ"

        val dlg = AlertDialog.Builder(this)
            .setTitle("// СЕТЬ //")
            .setView(view)
            .setPositiveButton("ЗАКРЫТЬ", null)
            .create()

        btnSrv.setOnClickListener {
            if (NetworkManager.isServerRunning()) {
                NetworkManager.stopAll()
                btnSrv.text = "▶ СЕРВЕР"
                logText.append("\n// сервер остановлен //")
            } else {
                val port = portEdit.text.toString().toIntOrNull() ?: NetworkManager.DEFAULT_PORT
                val key  = keyEdit.text.toString()
                NetworkManager.startServer(port, key)
                btnSrv.text = "■ СТОП"
                logText.append("\n// запуск сервера... //")
            }
        }

        btnCli.setOnClickListener {
            if (NetworkManager.isConnected()) {
                NetworkManager.stopAll()
                btnCli.text = "⇒ ПОДКЛЮЧИТЬСЯ"
                updateStatus()
            } else {
                val host = hostEdit.text.toString().trim()
                if (host.isEmpty()) { Toast.makeText(this,"// введи IP //",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val port = portEdit2.text.toString().toIntOrNull() ?: NetworkManager.DEFAULT_PORT
                val key  = keyEdit2.text.toString()
                val prefs = getSharedPreferences("hack_msg", MODE_PRIVATE)
                val name  = prefs.getString("name","ANON") ?: "ANON"
                logText.append("\n// подключение к $host... //")
                NetworkManager.connectTo(host, port, key, name) { ok ->
                    runOnUiThread {
                        if (ok) {
                            btnCli.text = "■ ОТКЛ"
                            logText.append("\n// подключено! //")
                        } else {
                            logText.append("\n// не удалось подключиться //")
                        }
                        updateStatus()
                    }
                }
            }
        }

        dlg.show()
    }

    private fun updateStatus() {
        when {
            NetworkManager.isServerRunning() -> {
                statusDot.setTextColor(resources.getColor(R.color.green, theme))
                statusText.text = "СЕРВЕР · ${NetworkManager.connectedCount()} клиентов"
            }
            NetworkManager.isConnected() -> {
                statusDot.setTextColor(resources.getColor(R.color.green, theme))
                statusText.text = "ПОДКЛЮЧЁН"
            }
            else -> {
                statusDot.setTextColor(resources.getColor(R.color.ddg, theme))
                statusText.text = "ОФЛАЙН"
            }
        }
    }

    // NetworkManager.Listener
    override fun onConnected(addr: String) = runOnUiThread { updateStatus() }
    override fun onDisconnected(addr: String) = runOnUiThread { updateStatus() }
    override fun onMessage(packet: JSONObject) = runOnUiThread {
        // Badge update could go here
    }
    override fun onLog(msg: String) {}
    override fun onError(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        NetworkManager.listener = this
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop network on activity destroy — let it run
    }
}
