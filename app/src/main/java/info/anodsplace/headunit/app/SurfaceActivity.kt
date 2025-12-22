package info.anodsplace.headunit.app

import android.app.Activity
import android.os.Bundle

import info.anodsplace.headunit.databinding.ActivityHeadunitBinding
import info.anodsplace.headunit.utils.hideSystemUI
import info.anodsplace.headunit.view.ProjectionView


abstract class SurfaceActivity : Activity() {

    private lateinit var binding: ActivityHeadunitBinding
    protected val surface: ProjectionView
        get() = binding.surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityHeadunitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.setOnSystemUiVisibilityChangeListener { window.decorView.hideSystemUI() }
        window.decorView.hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.hideSystemUI()
    }
}
