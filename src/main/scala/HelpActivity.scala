package org.mackler.metronome

class HelpActivity extends Activity with TypedActivity {

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    val helpPage = new android.webkit.WebView(this)
    setContentView(helpPage)
    helpPage.loadUrl("file:///android_res/raw/help.html")
  }

}
