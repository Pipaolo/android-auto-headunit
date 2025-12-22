package info.anodsplace.headunit.app

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

import info.anodsplace.headunit.App
import info.anodsplace.headunit.databinding.ActivityHeadunitBinding
import info.anodsplace.headunit.utils.hideSystemUI
import info.anodsplace.headunit.view.BaseProjectionView
import info.anodsplace.headunit.view.ProjectionViewFactory


abstract class SurfaceActivity : Activity() {

    private lateinit var binding: ActivityHeadunitBinding
    private lateinit var projectionView: BaseProjectionView
    
    protected val surface: BaseProjectionView
        get() = projectionView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityHeadunitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Create projection view based on settings
        val settings = App.provide(this).settings
        val container = binding.projectionContainer
        projectionView = ProjectionViewFactory.createAndAttach(container, settings)
        
        // Apply margins based on settings after layout is complete
        binding.root.post {
            val displayWidth = binding.root.width
            val displayHeight = binding.root.height
            ProjectionViewFactory.applyMarginsFromSettings(projectionView, settings, displayWidth, displayHeight)
        }
        
        window.decorView.setOnSystemUiVisibilityChangeListener { window.decorView.hideSystemUI() }
        window.decorView.hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.hideSystemUI()
    }
}