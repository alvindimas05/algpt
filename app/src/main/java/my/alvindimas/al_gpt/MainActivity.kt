package my.alvindimas.al_gpt

import android.database.sqlite.SQLiteDatabase
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private var interact = false
    private lateinit var api_url: String
    private lateinit var prompt: EditText
    private lateinit var message: EditText
    private lateinit var progress: ProgressBar
    private lateinit var db: SQLiteDatabase
    private lateinit var default_prompt: String

    private lateinit var btn_send: ImageButton
    private lateinit var btn_reset: ImageButton
    private lateinit var btn_regenerate: AppCompatButton
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        api_url = resources.getString(R.string.api_url)
        default_prompt = resources.getString(R.string.default_prompt)
        prompt = findViewById(R.id.prompt)
        message = findViewById(R.id.message)
        progress = findViewById(R.id.chat_progress)

        val pref = getPreferences(MODE_PRIVATE)
        findViewById<EditText>(R.id.prompt).setText(pref.getString("prompt", default_prompt))
        findViewById<EditText>(R.id.user_display_name).setText(pref.getString("user_name", "User"))
        findViewById<EditText>(R.id.ai_display_name).setText(pref.getString("ai_name", "Assistant"))

        btn_send = findViewById(R.id.btn_send)
        btn_reset = findViewById(R.id.btn_reset)
        btn_regenerate = findViewById(R.id.btn_regenerate)
        btn_send.setOnClickListener { onClick(it) }
        btn_reset.setOnClickListener { onReset() }
        btn_regenerate.setOnClickListener { onRegenerate() }

        db = DBHelper(this).writableDatabase
        resetChats()
    }
    private fun resetChats(){
        val c = db.rawQuery("SELECT * FROM chats", null)
        if(!c.moveToFirst()) return

        interact = true
        btn_regenerate.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.chat_layout).removeAllViews()
        findViewById<LinearLayout>(R.id.prompt_layout).visibility = View.GONE
        while (!c.isAfterLast){
            addMessage(c.getString(1), c.getLong(2).toInt() == 1, false)
            c.moveToNext()
        }
        c.close()
    }
    private fun onRegenerate(){
        db.execSQL("DELETE FROM chats WHERE id = (SELECT MAX(id) FROM chats) AND isUser = 0")
        resetChats()
        setResponse(true)
    }
    private fun onReset(){
        if(!interact){
            Toast.makeText(this, "Nothing to reset!", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d("Check Reset", "1")
        AlertDialog.Builder(this)
            .setTitle("Warning!")
            .setMessage("Are you sure you want to reset this chats?")
            .setPositiveButton("Yes") { _, _ ->
                run {
                    db.execSQL("DELETE FROM chats")
                    recreate()
                }
            }
            .setNegativeButton("No", null).show()
    }
    private fun addMessage(msg: String, isUser: Boolean = true, insert: Boolean = true){
        if(insert){
            val statement = db.compileStatement("INSERT INTO chats (chat, isUser) VALUES (?, ?)")
            statement.bindString(1, msg)
            statement.bindLong(2, if(isUser) 1 else 0)
            statement.executeInsert()
        }

        val layout = findViewById<LinearLayout>(R.id.chat_layout)
        val inflate = layoutInflater.inflate(R.layout.main_chat, layout, false)
        val pref = getPreferences(MODE_PRIVATE)
        val username = pref.getString("user_name", "User")
        val ainame = pref.getString("ai_name", "Assistant")

        if(!isUser) inflate.setBackgroundColor(this.getColor(R.color.chat_background))
        inflate.findViewById<TextView>(R.id.chat_role).text = if(isUser) "$username :" else "$ainame :"
        inflate.findViewById<TextView>(R.id.chat_message).text = msg
        layout.addView(inflate)
    }
    private fun onInteract(){
        interact = true
        findViewById<LinearLayout>(R.id.prompt_layout).visibility = View.GONE
        val username = findViewById<EditText>(R.id.user_display_name).text.toString()
        val ainame = findViewById<EditText>(R.id.ai_display_name).text.toString()

        val pref = getPreferences(MODE_PRIVATE).edit()
        val prom = prompt.text.toString()
        pref.putString("prompt", prom.ifEmpty { "You are a helpful assistant" })
        pref.putString("user_name", username.ifEmpty { "User" })
        pref.putString("ai_name", ainame.ifEmpty { "Assistant" })
        pref.apply()
    }
    private fun onClick(v: View){
        if(message.text.toString().isEmpty()) return
        v.isClickable = false

        if(!interact) onInteract()
        setResponse()
    }
    private fun setResponse(isRegenerate: Boolean = false){
        btn_regenerate.visibility = View.GONE
        progress.visibility = View.VISIBLE

        val body = JSONObject()
        val msgs = JSONArray()
        val pref = getPreferences(MODE_PRIVATE)
        msgs.put(JSONObject().put("role", "system")
            .put("content", pref.getString("prompt", default_prompt)))

        val c = db.rawQuery("SELECT * FROM chats", null)
        if(c.moveToFirst()) {
            while (!c.isAfterLast){
                msgs.put(JSONObject().put("role", if(c.getLong(2).toInt() == 1) "user" else "assistant")
                    .put("content", c.getString(1)))
                c.moveToNext()
            }
            c.close()
        }
        val msg = message.text.toString()

        if(!isRegenerate){
            msgs.put(JSONObject().put("role", "user")
                .put("content", msg))
            addMessage(message.text.toString())
        }
        body.put("messages", msgs)
        val req = Request.Builder()
            .url(api_url)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        message.text.clear()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        client.newCall(req).enqueue(object: Callback {
            private fun afterResponse(success: Boolean = true){
                if(success) btn_regenerate.visibility = View.VISIBLE
                progress.visibility = View.GONE
                btn_send.isClickable = true
            }
            override fun onResponse(call: Call, response: Response) {
                val message = response.body?.string()
                this@MainActivity.runOnUiThread {
                    if(message != null) addMessage(message, false)
                    afterResponse()
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                this@MainActivity.runOnUiThread {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Terjadi kesalahan dengan server!", Toast.LENGTH_SHORT).show()
                    afterResponse(false)
                }
            }
        })
    }
}