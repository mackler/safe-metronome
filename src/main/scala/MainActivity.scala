package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {
  import MainActivity._

  private val mHandler = new android.os.Handler

  private var mTempo = 0

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    // seek_bar is of different types between the landscape and portrait orientation
    findViewById(R.id.seek_bar).asInstanceOf[SeekBar].setOnSeekBarChangeListener(new OnSeekBarChangeListener {
      def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	if (fromUser) {
	  setTempoNumberDisplay(progress + 32)
	  mainActor ! SetTempo(mTempo)
	}

      }
      def onStartTrackingTouch(seekBar: SeekBar) {}; def onStopTrackingTouch(seekBar: SeekBar) {}
    })

    class LongClickAdjustListener(button: TypedResource[Button], amount: Int) extends OnLongClickListener {
      def onLongClick(v: View) = { mHandler.post(new Runnable { def run {
	adjustTempo(amount)
	if (findView(button).isPressed) mHandler.post(this)
      }})}
    }
			       
    findView(TR.slower_button).setOnLongClickListener(new LongClickAdjustListener(TR.slower_button, -1))
    findView(TR.faster_button).setOnLongClickListener(new LongClickAdjustListener(TR.faster_button, 1))

    mainActor ! SetUi(this)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main_activity, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.preferences ⇒
        (new SoundPickerFragment).show(getFragmentManager.beginTransaction(), "soundChooser")
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

  def setTempoDisplay(newTempo: Float) {
    val tempoInt = newTempo.round
    setTempoNumberDisplay(tempoInt)
    findViewById(R.id.seek_bar).asInstanceOf[SeekBar].setProgress(tempoInt - 32)
  }

  def setTempoNumberDisplay(newTempo: Int) = {
    mTempo = if (newTempo < MainActor.MIN_TEMPO) MainActor.MIN_TEMPO
             else if (newTempo > MainActor.MAX_TEMPO) MainActor.MAX_TEMPO
             else newTempo
    findView(TR.tempo).setText(mTempo.toString)
    findView(TR.marking).setText(marking(mTempo))
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

  def adjustTempo(delta: Int) {
    setTempoDisplay(mTempo + delta)
    mainActor ! SetTempo(mTempo)
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
	setTempoDisplay ( newTempo )
	mainActor ! SetTempo(mTempo)
	view.setBackgroundResource(android.R.color.holo_orange_light)
	mainActor ! Start
      } else {
	val ft = getFragmentManager.beginTransaction
	val fragment = getFragmentManager.findFragmentByTag("startTempo")
	if (newTempo < mTempo) {
	  startChopsBuilder ( newTempo, fragment.asInstanceOf[StartingTempoDialog].minutes )
	  setTempoDisplay ( newTempo )
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

  /* Stuff for ChopsBuilder ChopsBuilder™ */

  def configureChopsBuilder(view: View) { showCountdownDialog() }

  def showCountdownDialog() {
    val ft = getFragmentManager.beginTransaction
    ft.addToBackStack("chopsBuilder")
    val newFragment = CountdownDialog.newInstance(5)
    newFragment.show(ft,"countdown")
  }

  def showStartingTempoDialog(countDown: Int) {
    val ft = getFragmentManager.beginTransaction
    val countdownFragment = getFragmentManager.findFragmentByTag("countdown")
    if (countdownFragment != null) ft.remove(countdownFragment)
    ft.addToBackStack("chopsBuilder")
    val tempoGuess = (((mTempo - 32) / 2) + 32).round.toInt
    val newFragment = StartingTempoDialog.newInstance(countDown, tempoGuess)
    newFragment.show(ft,"startTempo")
  }

  def startChopsBuilder(startTempo: Int, countdownMinutes: Int) {
    logD(s"activity's startChopsBuilder() called, startTempo $startTempo")
    val ft = getFragmentManager.beginTransaction
    val countdownFragment = getFragmentManager.findFragmentByTag("startTempo")
    ft.remove(countdownFragment)
    ft.commit()
    getFragmentManager.popBackStack("chopsBuilder",1)

    displayChopsBuilderData(mTempo, s"$countdownMinutes:00")
    setTempoDisplay(startTempo)
    displayStartButton(false)
    mainActor ! BuildChops(startTempo, countdownMinutes)
  }

/*
  def startChopsBuilder(startTempo: Int, countdownMinutes: Int) {
    logD(s"activity's startChopsBuilder() called, startTempo $startTempo")
    displayChopsBuilderData(mTempo, s"$countdownMinutes:00")
    setTempoDisplay(startTempo)
    displayStartButton(false)
    mainActor ! BuildChops(startTempo, countdownMinutes)
  }*/

  def displayChopsBuilderData(tempo: Int, time: String) {
    findView(TR.target_tempo).setText(s"$tempo BPM")
    updateCountdown(time)
    findView(TR.chops_display).setVisibility(VISIBLE)
    findView(TR.chops_button).setVisibility(INVISIBLE)
  }

  def updateCountdown(time: String) { findView(TR.time_left).setText(time) }

  def cancelChopsBuilder(view: View) {
    mainActor ! ChopsCancel
    clearBuilder()
  }

  def clearBuilder() {
    findView(TR.chops_display).setVisibility(INVISIBLE)
    findView(TR.chops_button).setVisibility(VISIBLE)
  }

}

object MainActivity {
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
