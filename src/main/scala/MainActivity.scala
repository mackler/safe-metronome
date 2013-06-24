package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {

//  val pathShape = new android.graphics.drawable.shapes.PathShape(SliderPath, 1, 1)

  private val mHandler = new android.os.Handler

  private var mTempo = 0
  def setTempo(newTempo: Int) = {
    mTempo = if (newTempo < MainActor.MIN_TEMPO) MainActor.MIN_TEMPO
             else if (newTempo > MainActor.MAX_TEMPO) MainActor.MAX_TEMPO
             else newTempo
    findView(TR.tempo).setText(mTempo.toString)
    findView(TR.marking).setText(marking(mTempo))
  }

  private def adjustTempo(delta: Int) {
    setTempo(mTempo + delta)
    setSeek(mTempo-32)
    mainActor ! SetTempo(mTempo)
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    val onSeekBarChangeListener = new OnSeekBarChangeListener {
      def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	val newTempo = progress + 32
	setTempo(newTempo)
	if (fromUser) mainActor ! SetTempo(mTempo)

      }
      def onStartTrackingTouch(seekBar: SeekBar) {}; def onStopTrackingTouch(seekBar: SeekBar) {}
    }

    // seek_bar is of different types between the landscape and portrait orientation
    findViewById(R.id.seek_bar).asInstanceOf[SeekBar].setOnSeekBarChangeListener(onSeekBarChangeListener)

    findView(TR.slower_button).setOnLongClickListener(new android.view.View.OnLongClickListener { def onLongClick(v: View) = {
      mHandler.post(new LongPressRunnable(false))
      true
    }})

    findView(TR.faster_button).setOnLongClickListener(new android.view.View.OnLongClickListener { def onLongClick(v: View) = {
      mHandler.post(new LongPressRunnable(true))
      true
    }})

    mainActor ! SetUi(this)
  }

  def cancelChops(view: View) {
    mainActor ! ChopsCancel
    clearBuilder()
  }

  private class LongPressRunnable(faster: Boolean) extends Runnable { def run {
    val (amount, button) = if (faster)
      (1, findView(TR.faster_button))
    else
      (-1, findView(TR.slower_button))
    adjustTempo(amount)
    if (button.isPressed) mHandler.post(this)
  }}

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main_activity, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.preferences ⇒
        (new SoundFragment).show(getFragmentManager.beginTransaction(), "soundChooser")
        true
      case R.id.about ⇒
        (new AboutFragment).show(getFragmentManager.beginTransaction(), "about")
        true
      case _ ⇒ super.onOptionsItemSelected(item)
    }
  }

  override def onPause() {
    super.onPause()
    mainActor ! SavePreferences(getPreferences(MODE_PRIVATE))
    mainActor ! Stop
    displayStartButton(true)
  }

  def setSeek(progress: Int) {
    findViewById(R.id.seek_bar).asInstanceOf[SeekBar].setProgress(progress)
  }

  def configureChopsBuilder(view: View) { showCountdownDialog() }

  def showCountdownDialog() {
    (new CountdownFragment).show(getFragmentManager.beginTransaction(), "countdown")
  }

  def displayChopsBuilderData(targetTempo: Int, timeLeftMillis: Int) {
    findView(TR.target_tempo).setText(s"$mTempo BPM")
    val timeLeftSeconds = timeLeftMillis / 1000
    val minutes = timeLeftSeconds / 60
    val seconds = timeLeftSeconds % 60
    findView(TR.time_left).setText(s"$minutes:${seconds.formatted("%02d")}")
    findView(TR.chops_display).setVisibility(VISIBLE)
    findView(TR.chops_button).setVisibility(INVISIBLE)
  }

  def startChopsBuilder(startTempo: Int, countdownMinutes: Int) {
    displayChopsBuilderData(mTempo, countdownMinutes * 60000)
    setTempo(startTempo)
    setSeek(mTempo-32)
    displayStartButton(false)
    mainActor ! BuildChops(startTempo, countdownMinutes)
  }

  def showStartDialog(countDown: Int) {
    val ft = getFragmentManager.beginTransaction
    val countdownFragment = getFragmentManager.findFragmentByTag("countdown")
    ft.remove(countdownFragment)
    (new StartTempoFragment(mTempo-1, countDown)).show(ft, "startTempo")
  }

  def toggle(view: View) {
    val startString = getString(R.string.start)
    val stopString = getString(R.string.stop)
    findView(TR.control_button).getText match {
      case `startString` ⇒
        displayStartButton(false)
        mainActor ! Start
      case `stopString` ⇒
        displayStartButton(true)
        mainActor ! Stop
    }
  }

  def clearBuilder() {
    findView(TR.chops_display).setVisibility(INVISIBLE)
    findView(TR.chops_button).setVisibility(VISIBLE)
  }

  def updateCountdown(seconds: Int) {
    findView(TR.time_left).setText(
      (seconds/60).toString + ":" + (seconds%60).formatted("%02d")
    )
  }

  private var tapped: Long = 0
  def onTap(view: View) {
    if (tapped == 0) {
      tapped = System.currentTimeMillis
      view.setBackgroundResource(android.R.color.holo_blue_bright)
      mHandler.postDelayed(new Runnable { def run {
	tapped = 0 
	view.setBackgroundResource(android.R.color.holo_orange_light)
      }}, (60000/32) )
    } else {
      val durationInMillis = System.currentTimeMillis - tapped
      tapped = 0
      val newTempo = (60000.0 / durationInMillis ).round.toInt
      if (view == findView(TR.tap_button)) {
	setTempo ( newTempo )
	setSeek(mTempo - 32)
	mainActor ! SetTempo(mTempo)
	view.setBackgroundResource(android.R.color.holo_orange_light)
	mainActor ! Start
      } else {
	val ft = getFragmentManager.beginTransaction
	val fragment = getFragmentManager.findFragmentByTag("startTempo")
	if (newTempo < mTempo) {
	  startChopsBuilder (
	    newTempo, fragment.asInstanceOf[StartTempoFragment].countDown
	  )
	  setTempo ( newTempo )
	  setSeek(mTempo - 32)
	}
	ft.remove(fragment)
	ft.commit()
      }
      displayStartButton(false)
    }
  }

  private def displayStartButton(start: Boolean) {
    val button = findView(TR.control_button)
    start match {
      case true ⇒
        button.setText(R.string.start)
        button.setBackgroundResource(android.R.color.holo_green_light)

      case false ⇒
        button.setText(R.string.stop)
        button.setBackgroundResource(android.R.color.holo_red_dark)
    }
  }

  def faster(view: View) { adjustTempo(1) }
  def slower(view: View) { adjustTempo(-1) }

  private def marking(tempo: Int): String = {
    if (tempo < 40) "Grave"
    else if (tempo < 45) "Lento"
    else if (tempo < 50) "Largo"
    else if (tempo < 55) "Larghetto"
    else if (tempo < 64) "Adagio"
    else if (tempo < 69) "Adagietto"
    else if (tempo < 73) "Andante moderato"
    else if (tempo < 78) "Andante"
    else if (tempo < 83) "Andantino"
    else if (tempo < 86) "Marcia moderato"
    else if (tempo < 98) "Moderato"
    else if (tempo < 109) "Allegretto"
    else if (tempo < 132) "Allegro"
    else if (tempo < 140) "Vivace"
    else if (tempo < 150) "Allegrissimo"
    else if (tempo < 178) "Presto"
    else "Prestissimo"
  }

}

/*
object SliderPath extends android.graphics.Path {
  moveTo(0,0)
  lineTo(100,0)
  lineTo(90,100)
  lineTo(10,100)
  close()
}*/
