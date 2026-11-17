package il.calllock

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "calllock"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("enabled", v).apply()

    fun showCard(ctx: Context) = sp(ctx).getBoolean("showCard", true)
    fun setShowCard(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("showCard", v).apply()

    /** 0..100 */
    fun cardAlpha(ctx: Context) = sp(ctx).getInt("cardAlpha", 35)
    fun setCardAlpha(ctx: Context, v: Int) = sp(ctx).edit().putInt("cardAlpha", v).apply()

    fun lockX(ctx: Context) = sp(ctx).getFloat("lockX", -1f)
    fun lockY(ctx: Context) = sp(ctx).getFloat("lockY", -1f)
    fun setLockPos(ctx: Context, x: Float, y: Float) =
        sp(ctx).edit().putFloat("lockX", x).putFloat("lockY", y).apply()

    fun resetLockPos(ctx: Context) = sp(ctx).edit().remove("lockX").remove("lockY").apply()
}
