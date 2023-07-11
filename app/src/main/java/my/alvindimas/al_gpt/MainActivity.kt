package my.alvindimas.al_gpt

import android.content.DialogInterface
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

class MainActivity : AppCompatActivity() {
    private var interact = false
    private lateinit var api_url: String
    private lateinit var prompt: EditText
    private lateinit var message: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        api_url = resources.getString(R.string.api_url)
        prompt = findViewById(R.id.prompt)
        message = findViewById(R.id.message)
        progress = findViewById(R.id.chat_progress)

        val db = DBHelper(this).writableDatabase
        val c = db.rawQuery("SELECT * FROM chats", null)

        if(c.moveToFirst()) {
            interact = true
            findViewById<LinearLayout>(R.id.prompt_layout).visibility = View.GONE
            while (!c.isAfterLast){
                addMessage(c.getString(0), c.getLong(1).toInt() == 1, false)
                c.moveToNext()
            }
            c.close()
        }
        val pref = getPreferences(MODE_PRIVATE)
        findViewById<EditText>(R.id.prompt).setText(pref.getString("prompt", resources.getString(R.string.default_prompt)))
        findViewById<EditText>(R.id.user_display_name).setText(pref.getString("user_name", "User"))
        findViewById<EditText>(R.id.ai_display_name).setText(pref.getString("ai_name", "Assistant"))

        findViewById<ImageButton>(R.id.btn_send).setOnClickListener { onClick(it) }
        findViewById<ImageButton>(R.id.btn_reset).setOnClickListener { onReset(it) }
    }
    private fun onReset(v: View){
        if(!interact){
            Toast.makeText(this, "Nothing to reset!", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Warning!")
            .setMessage("Are you sure you want to reset this chats?")
            .setPositiveButton("Yes", DialogInterface.OnClickListener { _, _ ->
                run {
                    val db = DBHelper(this).writableDatabase
                    db.execSQL("DELETE FROM chats")
                    recreate()
                }
            })
            .setNegativeButton("No", null).show()
    }
    private fun addMessage(msg: String, isUser: Boolean = true, insert: Boolean = true){
        if(insert){
            val db = DBHelper(this).writableDatabase
            val statement = db.compileStatement("INSERT INTO chats VALUES (?, ?)")
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
        pref.putString("prompt", prompt.text.toString())
        pref.putString("user_name", username.ifEmpty { "User" })
        pref.putString("ai_name", ainame.ifEmpty { "Assistant" })
        pref.apply()
    }
    private fun onClick(v: View){
        v.isClickable = false
        progress.visibility = View.VISIBLE
        if(!interact) onInteract()

        val body = JSONObject()
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system")
            .put("content", prompt.text.toString()))

        val db = DBHelper(this).writableDatabase
        val c = db.rawQuery("SELECT * FROM chats", null)
        if(c.moveToFirst()) {
            while (!c.isAfterLast){
                msgs.put(JSONObject().put("role", if(c.getLong(1).toInt() == 1) "user" else "assistant")
                    .put("content", c.getString(0)))
                c.moveToNext()
            }
            c.close()
        }
        msgs.put(JSONObject().put("role", "user")
            .put("content", message.text.toString()))
        body.put("messages", msgs)

        addMessage(message.text.toString())
        val req = Request.Builder()
            .url(api_url)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        message.text.clear()
        OkHttpClient().newCall(req).enqueue(object: Callback {
            override fun onResponse(call: Call, response: Response) {
                val message = response.body?.string()
                this@MainActivity.runOnUiThread {
                    if(message != null) addMessage(message, false)
                    progress.visibility = View.GONE
                    v.isClickable = true
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                this@MainActivity.runOnUiThread {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Terjadi kesalahan dengan server!", Toast.LENGTH_SHORT).show()
                    progress.visibility = View.GONE
                    v.isClickable = true
                }
            }
        })
    }
}