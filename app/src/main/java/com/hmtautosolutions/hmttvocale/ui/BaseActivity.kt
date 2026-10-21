package com.hmtautosolutions.hmttvocale.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.hmtautosolutions.hmttvocale.LocaleHelper
import com.hmtautosolutions.hmttvocale.Prefs

/** Applies the user's chosen language (and RTL for Arabic) to every screen. */
abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs(newBase).language))
    }
}
