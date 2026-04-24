package com.hack.messenger

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class ChatActivity : AppCompatActivity(), NetworkManager.Listener {

    private lateinit var chatId: String
    private lateinit var chatTitle: String
    private lateinit var msgContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputEdit: EditText
    private lateinit var btnSend: Button
    private lateinit var btnAttach: Button
    private lateinit var titleText: TextView
    private lateinit var myName: String

    private val messages = mutableListOf<JSONObject>()
    private val PICK_FILE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatId    = intent.getStringExtra("chat_id") ?: "general"
        chatTitle = intent.getStringExtra("chat_title") ?: "// ЧАТ //"
        myName    = getSharedPreferences("hack_msg", MODE_PRIVATE).getString("name","ANON") ?: "ANON"

        msgContainer = findViewById(R.id.msgContainer)
        scrollView   = findViewById(R.id.scrollView)
        inputEdit    = findViewById(R.id.inputEdit)
        btnSend      = findViewById(R.id.btnSend)
        btnAttach    = findViewById(R.id.btnAttach)
        titleText    = findViewById(R.id.chatTitle)

        titleText.text = chatTitle
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnSend.setOnClickListener { sendText() }
        btnAttach.setOnClickListener { pickFile() }
        inputEdit.setOnEditorActionListener { _, _, _ -> sendText(); true }

        NetworkManager.listener = this

        // Load history
        messages.addAll(HistoryManager.load(chatId))
        messages.forEach { addBubble(it) }
        scrollBottom()
    }

    // ── SEND ────────────────────────────────────────────────────────
    private fun sendText() {
        val text = inputEdit.text.toString().trim()
        if (text.isEmpty()) return
        inputEdit.text.clear()
        val pkt = JSONObject().apply {
            put("type","text"); put("sender", myName)
            put("text", text); put("time", NetworkManager.timeNow())
            put("chat", chatId)
        }
        addBubble(pkt, mine = true)
        messages.add(pkt)
        HistoryManager.save(chatId, messages)
        NetworkManager.sendPacket(pkt)
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        startActivityForResult(intent, PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            sendFile(uri)
        }
    }

    private fun sendFile(uri: Uri) {
        val cr = contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val name = uri.lastPathSegment ?: "file"

        try {
            val bytes = cr.openInputStream(uri)?.readBytes() ?: return
            if (bytes.size > 10_000_000) {
                Toast.makeText(this,"// макс 10МБ //", Toast.LENGTH_SHORT).show(); return
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val isImage = mime.startsWith("image/")
            val pkt = JSONObject().apply {
                put("type", if (isImage) "image" else "file")
                put("sender", myName)
                put("filename", name)
                put("filesize", bytes.size)
                put("data", b64)
                put("time", NetworkManager.timeNow())
                put("chat", chatId)
            }
            addBubble(pkt, mine = true)
            messages.add(pkt)
            HistoryManager.save(chatId, messages)
            NetworkManager.sendPacket(pkt)
        } catch (e: Exception) {
            Toast.makeText(this, "// ошибка: $e //", Toast.LENGTH_SHORT).show()
        }
    }

    // ── BUBBLES ─────────────────────────────────────────────────────
    private fun addBubble(pkt: JSONObject, mine: Boolean = false) {
        val type = pkt.optString("type","text")

        if (type == "system") {
            addSystemMsg(pkt.optString("text",""))
            return
        }

        val isMine = mine || pkt.optString("sender") == myName
        val sender = pkt.optString("sender","???")
        val time   = pkt.optString("time","")

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0,4,0,4) }
            gravity = if (isMine) Gravity.END else Gravity.START
            setPadding(12,0,12,0)
        }

        // Sender label
        val senderLbl = TextView(this).apply {
            text = if (isMine) "// ВЫ //" else "// $sender //"
            textSize = 8f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resources.getColor(if (isMine) R.color.green else R.color.dg, theme))
            gravity = if (isMine) Gravity.END else Gravity.START
        }
        outer.addView(senderLbl)

        // Bubble
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(
                if (isMine) R.drawable.bubble_mine else R.drawable.bubble_other, theme)
            setPadding(24,16,24,16)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 2
            layoutParams = lp
        }

        when (type) {
            "text" -> {
                val tv = TextView(this).apply {
                    text = pkt.optString("text","")
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(resources.getColor(if (isMine) R.color.green else R.color.dg, theme))
                    setTextIsSelectable(true)
                }
                bubble.addView(tv)
            }
            "image" -> {
                try {
                    val bytes = Base64.decode(pkt.optString("data",""), Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val iv = ImageView(this).apply {
                        setImageBitmap(bmp)
                        layoutParams = LinearLayout.LayoutParams(
                            resources.displayMetrics.widthPixels / 2,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                    }
                    bubble.addView(iv)
                } catch (_: Exception) {
                    bubble.addView(TextView(this).apply {
                        text = "[изображение]"
                        setTextColor(resources.getColor(R.color.dg, theme))
                    })
                }
            }
            "file" -> {
                val fname = pkt.optString("filename","файл")
                val fsize = pkt.optInt("filesize",0)
                val sizeStr = if (fsize > 1_048_576) "${fsize/1_048_576}МБ" else "${fsize/1024}КБ"
                val tv = TextView(this).apply {
                    text = "📄 $fname\n$sizeStr"
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(resources.getColor(R.color.dg, theme))
                }
                bubble.addView(tv)
            }
        }

        // Time
        val timeLbl = TextView(this).apply {
            text = time
            textSize = 8f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resources.getColor(R.color.ddg, theme))
            gravity = Gravity.END
        }
        bubble.addView(timeLbl)
        outer.addView(bubble)
        msgContainer.addView(outer)
        scrollBottom()
    }

    private fun addSystemMsg(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 9f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(resources.getColor(R.color.ddg, theme))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0,6,0,6) }
        }
        msgContainer.addView(tv)
        scrollBottom()
    }

    private fun scrollBottom() {
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ── NETWORK ─────────────────────────────────────────────────────
    override fun onConnected(addr: String) {}
    override fun onDisconnected(addr: String) {}
    override fun onMessage(packet: JSONObject) {
        if (packet.optString("chat") == chatId || packet.optString("type") == "system") {
            runOnUiThread {
                messages.add(packet)
                HistoryManager.save(chatId, messages)
                addBubble(packet)
            }
        }
    }
    override fun onLog(msg: String) {}
    override fun onError(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        NetworkManager.listener = this
    }
}
