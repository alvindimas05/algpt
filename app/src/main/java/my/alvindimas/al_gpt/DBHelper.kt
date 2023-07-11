package my.alvindimas.al_gpt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private val DATABASE_VERSION = 1
private val DATABASE_NAME = "algpt"
class DBHelper(
    context: Context
): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase?) {
        val sql = "CREATE TABLE IF NOT EXISTS chats (" +
                "chat TEXT NOT NULL," +
                "isUser INTEGER NOT NULL" +
                ")"
        db?.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS chats")
    }
}