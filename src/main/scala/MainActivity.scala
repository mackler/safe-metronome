package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {
  import MainActivity._

  private val mHandler = new android.os.Handler

  private var mTempo = 0
  private var mCountdownSeconds = 0

  /** The tempo can be set by tapping.  To do so, this variable will store the
   * time at which the first tap occurred, in order that it can be subtracted from
   * the time of the second tap.  The difference is the duration of one beat. */
  private var mTapped: Long = 0
  def tapTime: Long = mTapped

  private def acknowledgeFirstTap(view: View) {
    val timeSinceTap = System.currentTimeMillis - mTapped
    val delay = (60000/32) - timeSinceTap
    if (delay > 0) {
      view.setBackgroundResource(android.R.color.holo_blue_bright)
      mHandler.postDelayed(new Runnable { def run {
	mTapped = 0 
	view.setBackgroundResource(android.R.color.holo_orange_light)
      }}, delay )
    }
  }

  override def onResume() {
    super.onResume()
    if (mTapped != 0) {
      // We have to know whether or not the first tap came from the StartTempoDialog
      val startTempoFragment = getFragmentManager.findFragmentByTag("startTempo")
      if (startTempoFragment == null)
	acknowledgeFirstTap(findView(TR.tap_button))
      else acknowledgeFirstTap(startTempoFragment.getView.findViewById(R.id.dialog_tap_button))
    }
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    if (bundle != null) {
      mTapped = bundle.getLong("tapTime", 0)
    }

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

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putLong("tapTime", mTapped)
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

  def onTap(view: View) {
    if (mTapped == 0) {
      mTapped = System.currentTimeMillis
      acknowledgeFirstTap(view)
    } else {
      val durationInMillis = System.currentTimeMillis - mTapped
      mTapped = 0
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

  def incrementCountdown(view: View) {
    adjustCountdown(1)
  }

  def decrementCountdown(view: View) {
    adjustCountdown(-1)
  }

  private var mCountdownPattern: Option[Pattern] = None
  def countdownPattern: Pattern = {
    if (!mCountdownPattern.isDefined) mCountdownPattern = Option(patternCompile(":"))
    mCountdownPattern.get
  }

  private def adjustCountdown(adjustment: Int) {
/*    val timeLeft = findView(TR.time_left).getText
    val segments = countdownPattern.split(timeLeft, 2)
    val minutes = segments(0).toInt */
    val minutes = mCountdownSeconds / 60
    val seconds = mCountdownSeconds % 60
    val newMinutes = minutes + adjustment
    if (newMinutes < 60 && (newMinutes > 0 || newMinutes == 0 && seconds > 0)) {
//      val seconds = segments(1).toInt
      val newSeconds = (newMinutes * 60) + seconds
      mainActor ! SetCountdown( newSeconds )
      updateCountdown(newSeconds * 1000)
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
    val maxTempo = mTempo - 1
    val tempoGuess = (((mTempo - 32) / 2) + 32).round.toInt
    val newFragment = StartingTempoDialog.newInstance(countDown, tempoGuess, maxTempo)
    newFragment.show(ft,"startTempo")
  }

  def startChopsBuilder(startTempo: Int, countdownMinutes: Int) {
    logD(s"activity's startChopsBuilder() called, startTempo $startTempo")
    val ft = getFragmentManager.beginTransaction
    val countdownFragment = getFragmentManager.findFragmentByTag("startTempo")
    ft.remove(countdownFragment)
    ft.commit()
    getFragmentManager.popBackStack("chopsBuilder",1)

    displayChopsBuilderData(mTempo, countdownMinutes*60000)
    setTempoDisplay(startTempo)
    displayStartButton(false)
    mainActor ! BuildChops(startTempo, countdownMinutes)
  }

  def displayChopsBuilderData(tempo: Int, milliSeconds: Int) {
    findView(TR.target_tempo).setText(s"$tempo BPM")
    updateCountdown(milliSeconds)
    findView(TR.chops_display).setVisibility(VISIBLE)
    findView(TR.chops_button).setVisibility(INVISIBLE)
  }

  private def formattedTime(milliSeconds: Int): String = {
    val seconds = (milliSeconds/1000).round.toInt
    val displayMinutes = seconds / 60
    val displaySeconds = seconds % 60
    s"$displayMinutes:${displaySeconds.formatted("%02d")}"
  }

  def updateCountdown(milliSeconds: Int) {
    mCountdownSeconds = (milliSeconds/1000).round.toInt
    findView(TR.time_left).setText(formattedTime(milliSeconds))
  }

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
