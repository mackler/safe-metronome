package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {
  // between 32 and 252 (same as Korg KDM-2)
  var tempo: Int = 60

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)
    findView(TR.tempo).setText(tempo.toString)

    val seekBar = findView(TR.seek_bar)

    seekBar.setProgress(tempo - 32)

    val onSeekBarChangeListener = new OnSeekBarChangeListener {
      def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	mainActor ! SetTempo(progress + 32)
      }
      def onStartTrackingTouch(seekBar: SeekBar) {}
      def onStopTrackingTouch(seekBar: SeekBar) {}
    }

    findView(TR.seek_bar).setOnSeekBarChangeListener(onSeekBarChangeListener)

    mainActor ! SetUi(this)
  }

  def start(view: View) { mainActor ! Start(this) }
  def stop(view: View) { mainActor ! Stop }

  def displayTempo(tempo: Int) {
    findView(TR.tempo).setText(tempo.toString)
  }

}
