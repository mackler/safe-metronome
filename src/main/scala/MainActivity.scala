package org.mackler.metronome

class MainActivity extends Activity with TypedActivity {
  import MainActivity._

  private val mHandler = new android.os.Handler

  private var mTempo = 0

  override def onResume() {
    super.onResume()
    if (mTapped != 0) { // We're in the process of setting tempo by tapping
      // We have to know whether or not the first tap came from the StartTempoDialog
      val startTempoFragment = getFragmentManager.findFragmentByTag("startTempo")
      if (startTempoFragment == null)
	acknowledgeFirstTap(findView(TR.tap_button))
      else acknowledgeFirstTap(startTempoFragment.getView.findViewById(R.id.dialog_tap_button))
    }
  }

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    if (DEVELOPMENT_MODE ) {
      val display = getWindowManager.getDefaultDisplay
      val outMetrics = new android.util.DisplayMetrics
      display.getMetrics(outMetrics)

      val density  = getResources.getDisplayMetrics.density
      val dpHeight = outMetrics.heightPixels / density
      val dpWidth  = outMetrics.widthPixels / density
      logD(s"density: $density; dpHeight: $dpHeight; dpWidth: $dpWidth")
    }

    setContentView(R.layout.main)

    if (bundle != null) {
      mTapped = bundle.getLong("tapTime", 0)
    }

    // Can't use the typeed findView() here because the
    // seek_bar is of different types between the landscape and portrait orientation
    findViewById(R.id.seek_bar).asInstanceOf[SeekBar].
    setOnSeekBarChangeListener(new OnSeekBarChangeListener {
      def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	if (fromUser) {
	  setTempoNumberDisplay(progress + 32)
	  mainActor ! SetTempo(mTempo, System.currentTimeMillis)
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

    findView(TR.tap_button).setOnTouchListener(onTapListener)

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
      case R.id.accent ⇒
        (new AccentPickerFragment).show(getFragmentManager.beginTransaction(), "accentSetting")
        true
      case R.id.help ⇒
        val helpIntent = new android.content.Intent(this, classOf[HelpActivity])
        startActivity(helpIntent)
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
    mainActor ! Stop(System.currentTimeMillis)
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
    if (view == findView(TR.control_button)) {
      val startString = getString(R.string.start)
      val stopString = getString(R.string.stop)
      findView(TR.control_text).getText match {
	case `startString` ⇒ start()
	case `stopString` ⇒
          displayStartButton(true)
	  displayPauseButton(false)
          mainActor ! Stop(System.currentTimeMillis)
      }
    } else start() // was the ChopsBuilder™ unpause button
  }

  def setSound(which: Int) { mainActor ! SetSound(which) }

  def setAccent(which: Int) {
    mainActor ! SetAccent(which match {
      case 0 ⇒ 0
      case 1 ⇒ 2
      case 2 ⇒ 3
      case 3 ⇒ 4
      case 4 ⇒ 5
      case 5 ⇒ 6
      case 6 ⇒ 7
    })
  }

  def hideStartingProgress() {
    findView(TR.starting_progress).setVisibility(GONE)
  }

  def start() {
    displayPlayingButtons()
    mainActor ! Start(System.currentTimeMillis)
  }

  private def displayPauseButton(visible: Boolean) { visible match {
    case true ⇒
      findView(TR.chops_pause).setVisibility(VISIBLE)
      findView(TR.chops_unpause).setVisibility(GONE)
    case false ⇒
      findView(TR.chops_pause).setVisibility(GONE)
      findView(TR.chops_unpause).setVisibility(VISIBLE)
  }}

  def adjustTempo(delta: Int) {
    setTempoDisplay(mTempo + delta)
    mainActor ! SetTempo(mTempo, System.currentTimeMillis)
  }

  /** The tempo can be set by tapping.  To do so, this variable will store the
   * time at which the first tap occurred, in order that it can be subtracted from
   * the time of the second tap.  The difference is the duration of one beat. */
  private var mTapped: Long = 0
  //  def tapTime: Long = mTapped

  object onTapListener extends android.view.View.OnTouchListener {
    def onTouch(view: View, event: android.view.MotionEvent): Boolean = {
      if (event.getActionMasked == android.view.MotionEvent.ACTION_DOWN) {
	if (mTapped == 0) {
	  mainActor ! SingleTap
	  mTapped = System.currentTimeMillis
	  acknowledgeFirstTap(view)
	} else {
	  val durationInMillis = System.currentTimeMillis - mTapped
	  mTapped = 0
	  val newTempo = (60000.0 / durationInMillis ).round.toInt
	  // Taps might come from either the main display or the ChopsBuilder™ dialog:
	  if (view == findView(TR.tap_button)) {
	    setTempoDisplay ( newTempo )
	    mainActor ! SetTempo(mTempo, System.currentTimeMillis)
	    view.setBackgroundResource(R.color.tap_setting)
	    mainActor ! Start(System.currentTimeMillis)
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
	  displayPlayingButtons()
	}
	true
      } else false
    }
  }

  private def acknowledgeFirstTap(view: View) {
    val timeSinceTap = System.currentTimeMillis - mTapped
    val delay = (60000/32) - timeSinceTap
    if (delay > 0) {
      view.setBackgroundResource(R.color.tap_setting)
      mHandler.postDelayed(new Runnable { def run {
	mTapped = 0 
	view.setBackgroundResource(R.color.tap_inactive)
      }}, delay )
    }
  }

  def pauseChopsBuilder(view: View) {
    mainActor ! PauseChopsBuilder
    findView(TR.chops_pause).setVisibility(GONE)
    findView(TR.chops_unpause).setVisibility(VISIBLE)
  }

  def unpauseChopsBuilder(view: View) {
    displayStartButton(false)
    mainActor ! Start(System.currentTimeMillis)
  }

  private def currentCountdown: Tuple2[Int,Int] = {
    val timeLeft = findView(TR.time_left).getText
    val segments = countdownPattern.split(timeLeft, 2)
    val minutes = segments(0).toInt 
    val seconds = segments(1).toInt
    (minutes,seconds)
  }

  def incrementCountdown(view: View) {
    val (minutes,seconds) = currentCountdown
    val newMinutes = minutes + 1
    if (newMinutes < 60) {
      mainActor ! IncrementCountdown
      updateCountdown(newMinutes, seconds)
    }
  }

  def decrementCountdown(view: View) {
    val (minutes,seconds) = currentCountdown
    if (minutes > 0) {
      mainActor ! DecrementCountdown
      updateCountdown(minutes-1, seconds)
    }
  }

  private var mCountdownPattern: Option[Pattern] = None
  def countdownPattern: Pattern = {
    if (!mCountdownPattern.isDefined) mCountdownPattern = Option(patternCompile(":"))
    mCountdownPattern.get
  }

  private def displayPlayingButtons() {
      displayStartButton(false)
      displayPauseButton(true)
  }

  private def displayStartButton(start: Boolean) {
    val button = findView(TR.control_button)
    val icon = findView(TR.control_icon)
    val text = findView(TR.control_text)
    start match {
      case true ⇒
        text.setText(R.string.start)
        icon.setImageResource(R.drawable.ic_start)
        button.setBackgroundResource(R.color.go)

      case false ⇒
        text.setText(R.string.stop)
        icon.setImageResource(R.drawable.ic_stop)
        button.setBackgroundResource(R.color.stop)
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
    val ft = getFragmentManager.beginTransaction
    val countdownFragment = getFragmentManager.findFragmentByTag("startTempo")
    ft.remove(countdownFragment)
    ft.commit()
    getFragmentManager.popBackStack("chopsBuilder",1)

    displayChopsBuilderData(mTempo, countdownMinutes*60000)
    setTempoDisplay(startTempo)
    displayPlayingButtons()
    mainActor ! BuildChops(startTempo, countdownMinutes)
  }

  def displayChopsBuilderData(tempo: Int, milliSeconds: Int) {
    findView(TR.target_tempo).setText(s"$tempo BPM")
    updateCountdown(milliSeconds)
    findView(TR.chops_display).setVisibility(VISIBLE)
    findView(TR.chops_button).setVisibility(INVISIBLE)
  }

  private def formattedTime(milliSeconds: Int): String = {
    val seconds = (milliSeconds/1000) + (if (milliSeconds % 1000 == 0) 0 else 1)
    val displayMinutes = seconds / 60
    val displaySeconds = seconds % 60
    s"$displayMinutes:${displaySeconds.formatted("%02d")}"
  }

  def updateCountdown(milliSeconds: Int) {
    findView(TR.time_left).setText(formattedTime(milliSeconds))
  }

  private def updateCountdown(minutes: Int, seconds: Int) {
    updateCountdown((minutes*60 + seconds) * 1000)
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
